/*******************************************************************************
 * Copyright (c) 2015 Microsoft Research. All rights reserved. 
 *
 * The MIT License (MIT)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. 
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *   Markus Alexander Kuppe - initial API and implementation
 ******************************************************************************/
package tlc2.tool;

import java.util.concurrent.CyclicBarrier;

import tlc2.output.EC;
import tlc2.output.MP;

public class NoCoherenceWorker extends Worker implements IWorker {
	
	/**
	 * Multi-threading helps only when running on multiprocessors. TLC can
	 * pretty much eat up all the cycles of a processor running single threaded.
	 * We expect to get linear speedup with respect to the number of processors.
	 */
	private final CyclicBarrier finalization;
	private volatile long generated = 0L;
	private volatile long processed = 0L;
	private TLCState head;
	private TLCState tail;

	// SZ Feb 20, 2009: changed due to super type introduction
	public NoCoherenceWorker(int id, CyclicBarrier finalization, AbstractChecker tlc) {
		super(id,tlc);
		// Worker threads are daemon threads because TLC does NOT stop workers,
		// when one of the workers discovers a (safety) violation. Let w be the
		// worker from the set of workers that discovery a safety violation. w
		// will shift from state graph exploration to counterexample
		// reconstruction.
		// In the meantime, the remaining workers will continue state graph
		// exploration until the JVM terminates. This special form of eventual
		// consistency suffices for TLC. Its only downside is that we burn CPU
		// cycles when N-1 workers continue the state graph exploration even
		// though w has already discovered a violation. On the other hand, the
		// vast majority of time is spent on state graph exploration when no
		// violation has yet been found. Thus, the reduced thread contention
		// probably makes up for the extra CPU cycles at the end.
		this.setDaemon(true);
		
		this.finalization = finalization;
	}
	
	public NoCoherenceWorker(Worker oldWorker, CyclicBarrier finalization, TLCState state) {
		this(oldWorker.myGetId(), finalization, oldWorker.tlc);
		astCounts = oldWorker.astCounts;
		statesGenerated = oldWorker.statesGenerated;
		enqueueTop(state);
	}

   public NoCoherenceWorker(TLCState predErrState, CyclicBarrier finalization, AbstractChecker tlc) {
	   this((int) predErrState.uid, finalization, tlc);
	   this.head = predErrState;
   }

	/**
     * This method gets a state from the queue, generates all the
     * possible next states of the state, checks the invariants, and
     * updates the state set and state queue.
	 */
	public final void run() {
		try {
			while (true) {
				if (head == null) {
					finalization.await();
					return;
				}
				if (this.tlc.doNext(this.astCounts, this)) {
					return;
				}
				dequeue();
			}
		} catch (Throwable e) {
			// Something bad happened. Quit ...
			// Assert.printStack(e);
			if (this.tlc.setErrState(head, null, true)) {
				MP.printError(EC.GENERAL, e); // LL changed call 7 April
												// 2012
			}
			return;
		}
	}

	private void dequeue() {
		// Done with the state. Unlink the current head and make its
		// successor the new head of the list.
		final TLCState oldHead = head;
		head = head.next;
		oldHead.next = null;
		processed++;
	}

	public void enqueueTop(TLCState state) {
		assert state != null;
		if (head == null) {
			assert tail == null;
			head = state;
			tail = state;
		} else {
			head.next = state;
			tail = state;
		}
		generated++;
	}

	public void enqueue(TLCState state) {
		assert state != null;
		tail.next = state;
		tail = state;
		// received additional work
		generated++;
	}

	/**
	 * The length of the linked list of {@link TLCState}s started at head and
	 * ended at tail. This value is an approximation.
	 */
	public long unexplored() {
		return generated - processed;
	}

	public TLCState getHead() {
		return this.head;
	}

	void incrementStatesGenerated(long l) {
		this.statesGenerated += l;
	}

	long getStatesGenerated() {
		return this.statesGenerated;
	}
}
