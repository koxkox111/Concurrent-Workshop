package cp2022.solution;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import cp2022.base.Workplace;

public class WorkplaceKid extends Workplace {

    private Workplace ojciec;
    private CyclicBarrier barrier;
    private CountDownLatch latchWait;
    private CountDownLatch latchPusc;

    public WorkplaceKid(Workplace ojciec) {
        super(ojciec.getId());
        this.ojciec = ojciec;
        this.barrier = new CyclicBarrier(1);
        this.latchWait = new CountDownLatch(0);
        this.latchPusc = new CountDownLatch(0);
    }

    public void ustawBareira(CyclicBarrier b) {
        this.barrier = b;
    }

    public void ustawLatchWait(CountDownLatch cdl) {
        this.latchWait = cdl;
    }

    public void ustawLatchPusc(CountDownLatch cdl) {
        this.latchPusc = cdl;
    }

    @Override
    public void use() {

        try {
            latchPusc.countDown();
            latchWait.await();
            barrier.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        } catch (BrokenBarrierException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }

        ustawBareira(new CyclicBarrier(1));

        ojciec.use();

    }
}
