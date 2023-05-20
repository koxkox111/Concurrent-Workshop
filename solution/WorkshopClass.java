package cp2022.solution;

import cp2022.base.*;
import cp2022.base.Workplace;
import cp2022.base.Workshop;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;


public class WorkshopClass implements Workshop {

    private WorkplaceKid[] workplaces;
    private int N;
    private AtomicBoolean[] czyWolny;
    private ConcurrentHashMap<WorkplaceId, Integer> mapaWorkplaceId = new ConcurrentHashMap<WorkplaceId, Integer>();
    private ConcurrentHashMap<Long, WorkplaceId> mapaStanowisk = new ConcurrentHashMap<Long, WorkplaceId>();;
    private Semaphore mutex = new Semaphore(1);
    private ArrayLinkedList[] listaEnter;
    private ArrayLinkedList[] listaSwitchTo;
    private ConcurrentHashMap<Long, Semaphore> naWatek = new ConcurrentHashMap<Long, Semaphore>();
    private ConcurrentHashMap<Long, Boolean> czyPrzezCykl  = new ConcurrentHashMap<Long, Boolean>();


    private ConcurrentHashMap<Long, Integer> gdzieChceIsc = new ConcurrentHashMap<Long, Integer>();
    private ConcurrentHashMap<Integer, Long> ktoTuSiedzi = new ConcurrentHashMap<Integer, Long>();
    
    private ConcurrentHashMap<Long, Integer> czasWejscia = new ConcurrentHashMap<Long, Integer>();

    private LinkedList<Long> czekaSwitchTo = new LinkedList<Long>();
    private LinkedList<Long> czekaEnter = new LinkedList<Long>();
    private LinkedList<Long> blokujeEnter = new LinkedList<Long>();

    private int czas = 0;

    public WorkshopClass(Collection<Workplace> workplacesList) {

        N = workplacesList.size();
        workplaces = new WorkplaceKid[N];
        int it = 0;
        for (Workplace x : workplacesList) {
            workplaces[it] = new WorkplaceKid(x);
            it++;
        }
        czyWolny = new AtomicBoolean[N];
        listaEnter = new ArrayLinkedList[N];
        listaSwitchTo = new ArrayLinkedList[N];

        for (int i = 0; i < N; i++) {
            mapaWorkplaceId.put(workplaces[i].getId(), i);
            czyWolny[i] = new AtomicBoolean(true);
            listaEnter[i] = new ArrayLinkedList();
            listaSwitchTo[i] = new ArrayLinkedList();
        }
    }

    @Override
    public Workplace enter(WorkplaceId wid) {

        int gdzieIdziemy = mapaWorkplaceId.get(wid);
        Workplace wkp = workplaces[gdzieIdziemy];
        try {
            mutex.acquire();
            czas++;
            int obecnyCzas = czas;
            long numerWatek = Thread.currentThread().getId();
            czasWejscia.put(numerWatek, czas);
            naWatek.put(numerWatek, new Semaphore(0));
            czyPrzezCykl.put(numerWatek, false);
            int c = najdluzejczeka();
            if(c != -1 && c + 2 * N - 1 <= obecnyCzas){
                blokujeEnter.add(numerWatek);
                mutex.release();
                naWatek.get(numerWatek).acquire();

                mutex.acquire();
                blokujeEnter.remove(numerWatek);
            }
            if (czyWolny[gdzieIdziemy].compareAndSet(true, false)) {
                przesiad_sie(numerWatek, -1, gdzieIdziemy, wid);
                mutex.release();
            } else {
                czekaEnter.add(numerWatek);
                listaEnter[gdzieIdziemy].add(numerWatek);
                mutex.release();
                naWatek.get(numerWatek).acquire();
                mutex.acquire();
                czekaEnter.remove(numerWatek);
                obudzGagatka(obecnyCzas);

                przesiad_sie(numerWatek, -1, gdzieIdziemy, wid);
                mutex.release();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
        return wkp;
    }

    @Override
    public Workplace switchTo(WorkplaceId wid) {
        int gdzieIdziemy = mapaWorkplaceId.get(wid);
        long numerWatek = Thread.currentThread().getId();
        WorkplaceId widObecne = mapaStanowisk.get(numerWatek);
        int gdzieJestesmy = mapaWorkplaceId.get(widObecne);
        Workplace wkp = workplaces[gdzieIdziemy];

        if (wid == widObecne) {
            return wkp;
        }

        try {
            LinkedList<Long> watki = new LinkedList<Long>();
            LinkedList<Integer> stanowiska = new LinkedList<Integer>();
            mutex.acquire();
            int obecnyCzas = czas;
            czasWejscia.put(numerWatek, czas);

            gdzieChceIsc.put(numerWatek, gdzieIdziemy);
            stanowiska.add(gdzieIdziemy);
            while (true) {
                Integer ost_Stano = stanowiska.getLast();
                if (!ktoTuSiedzi.containsKey(ost_Stano)) {
                    break;
                }
                Long watek = ktoTuSiedzi.get(ost_Stano);
                if (!gdzieChceIsc.containsKey(watek)) {
                    break;
                }
                watki.add(watek);
                Integer stanowisko = gdzieChceIsc.get(watek);
                if (stanowisko == gdzieJestesmy) {
                    CyclicBarrier cb = new CyclicBarrier(watki.size() + 1);
                    for (Integer x : stanowiska) {
                        workplaces[x].ustawBareira(cb);
                    }
                    workplaces[gdzieJestesmy].ustawBareira(cb);
                    for (int i = 0; i < watki.size(); i++) {
                        Long wtk = watki.get(i);
                        Integer stan;
                        if (i + 1 < stanowiska.size())
                            stan = stanowiska.get(i + 1);
                        else
                            stan = gdzieJestesmy;
                        czyPrzezCykl.put(wtk, true);
                        listaSwitchTo[stan].remove(wtk);
                        naWatek.get(wtk).release();
                    }
                    przesiad_sie(numerWatek, gdzieJestesmy, gdzieIdziemy, wid);
                    mutex.release();
                    return wkp;
                }
                stanowiska.add(stanowisko);

            }
            if (czyWolny[gdzieIdziemy].compareAndSet(true, false)) {

                przesiad_sie(numerWatek, gdzieJestesmy, gdzieIdziemy, wid);
                CountDownLatch cdl = new CountDownLatch(1);
                workplaces[gdzieJestesmy].ustawLatchWait(cdl);
                workplaces[gdzieIdziemy].ustawLatchPusc(cdl);
                pobudka(gdzieJestesmy);

                mutex.release();

                return wkp;
            } else {
                czekaSwitchTo.add(numerWatek);
                listaSwitchTo[gdzieIdziemy].add(numerWatek);
                mutex.release();
                naWatek.get(numerWatek).acquire();
            }

            mutex.acquire();
            czekaSwitchTo.remove(numerWatek);
            obudzGagatka(obecnyCzas);
            if(czyPrzezCykl.get(numerWatek) == true){
                czyPrzezCykl.put(numerWatek, false);
            }else{
                CountDownLatch cdl = new CountDownLatch(1);
                workplaces[gdzieJestesmy].ustawLatchWait(cdl);
                workplaces[gdzieIdziemy].ustawLatchPusc(cdl);
                pobudka(gdzieJestesmy);
            }

            przesiad_sie(numerWatek, gdzieJestesmy, gdzieIdziemy, wid);
            mutex.release();
            return wkp;
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    @Override
    public void leave() {
        long nazwaPracownika = Thread.currentThread().getId();
        WorkplaceId wid = mapaStanowisk.get(nazwaPracownika);
        int numberObecny = mapaWorkplaceId.get(wid);
        try {
            mutex.acquire();
            pobudka(numberObecny);
            mutex.release();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public void pobudka(int numberObecny) {
        ktoTuSiedzi.remove(numberObecny);
        if (listaSwitchTo[numberObecny].size() != 0) {
            Long x = listaSwitchTo[numberObecny].removeFirst();
            naWatek.get(x).release();
        
        } else if (listaEnter[numberObecny].size() != 0) {
            Long x = listaEnter[numberObecny].removeFirst();
            naWatek.get(x).release();

        } else {
            czyWolny[numberObecny].set(true);
        }
    }

    public void przesiad_sie(Long obecnyWatek, int obecneMiejsce, int celMiejsca, WorkplaceId cel) {
        mapaStanowisk.put(obecnyWatek, cel);
        gdzieChceIsc.remove(obecnyWatek);
        ktoTuSiedzi.put(celMiejsca, obecnyWatek);
    }

    public int najdluzejczeka(){
        Long x = (long) -1;
        if(czekaEnter.size() > 0)
            x = czekaEnter.getFirst();
        Long y = (long) -1;
        if(czekaSwitchTo.size() > 0)
            y = czekaSwitchTo.getFirst();
        
        Integer a = -1;
        if(czasWejscia.containsKey(x))
            a = czasWejscia.get(x);
        Integer b = -1;
        if(czasWejscia.containsKey(y))
            b = czasWejscia.get(y);
        return Math.max(a, b);
    }

    public void obudzGagatka(int czas){
        int k = najdluzejczeka();
        if(k == -1){
            for(int i = 0 ; i < 2 * N - 1; i++){
                if(blokujeEnter.size() > 0){
                    Long x = blokujeEnter.removeFirst();
                    naWatek.get(x).release();
                }
                else{
                    break;
                }
            }
        }
        else if(k > czas){
            while(true){
                if(blokujeEnter.size() > 0){
                    Long x = blokujeEnter.getFirst();
                    if(czasWejscia.get(x) <= czas + 2 * N - 1 && czasWejscia.get(x) < k + 2 * N - 1 ){
                        x = blokujeEnter.removeFirst();
                        naWatek.get(x).release();
                    }
                    else{
                        break;
                    }
                }
                else{
                    break;
                }
            }
        }
    }
}
