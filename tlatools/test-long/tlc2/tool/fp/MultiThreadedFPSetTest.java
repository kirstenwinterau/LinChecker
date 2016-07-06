// Copyright (c) 2012 Markus Alexander Kuppe. All rights reserved.
package tlc2.tool.fp;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

import tlc2.tool.fp.generator.BatchedFingerPrintGenerator;
import tlc2.tool.fp.generator.FingerPrintGenerator;

public abstract class MultiThreadedFPSetTest extends AbstractFPSetTest {

	private static final int NUM_THREADS = Integer.getInteger(MultiThreadedFPSetTest.class.getName() + ".numThreads",
			Runtime.getRuntime().availableProcessors());
	private static final long INSERTIONS = Long.getLong(MultiThreadedFPSetTest.class.getName() + ".insertions",
			Integer.MAX_VALUE + 2L);

	protected static final long RNG_SEED = 15041980L;

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	public void setUp() throws Exception {
		super.setUp();
		System.out.println("Insertions: " + df.format(INSERTIONS)
				+ " (approx: " + df.format(INSERTIONS * FPSet.LongSize >> 20) + " GiB)");
		System.out.println("Thread count: " + NUM_THREADS);
	}
	
	/**
	 * Test filling a {@link FPSet} with random fingerprints using multiple
	 * threads in ordered batches
	 */
	@Test
	public void testMaxFPSetSizeRndBatched() throws Exception {
		doTest(BatchedFingerPrintGenerator.class);
	}
	
	/**
	 * Test filling a {@link FPSet} with max int + 2L random using multiple
	 * threads
	 */
	@Test
	public void testMaxFPSetSizeRnd() throws Exception {
		doTest(FingerPrintGenerator.class);
	}
	
	/**
	 * @param fpgClass
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	private void doTest(Class<? extends FingerPrintGenerator> fpgClass) throws Exception {
		final FPSet fpSet = getFPSetInitialized(NUM_THREADS);
		final CountDownLatch latch = new CountDownLatch(NUM_THREADS);

		final Constructor<?> constructor = fpgClass
				.getConstructor(new Class[] { MultiThreadedFPSetTest.class, int.class, FPSet.class, CountDownLatch.class, long.class, long.class });
		
		long seed = RNG_SEED;
		final FingerPrintGenerator[] fpgs = new FingerPrintGenerator[NUM_THREADS];
		for (int i = 0; i < fpgs.length; i++) {
			fpgs[i] = (FingerPrintGenerator) constructor.newInstance(
					this, i, fpSet, latch, seed++, INSERTIONS);
			Thread thread = new Thread(fpgs[i], "Producer#" + i);
			thread.start();
		}

		// wait for runnables/fpg to tear down the latch
		latch.await();

		endTimeStamp = new Date();
		
		long overallPuts = 0L;
		
		// print stats
		for (int i = 0; i < fpgs.length; i++) {
			final FingerPrintGenerator fpg = fpgs[i];
			long puts = fpg.getPuts();
			System.out.println("Producer: " + fpg.getId() + " puts: " + puts);
			System.out.println("puts/collisions: " + (double) (puts / fpg.getCollisions()));
			overallPuts += puts;
		}
		
		// Do not compare fpSet.size() to insertions as several FPGs might race
		// with the FPG that inserts the INSERTIONS element. Hence we count the
		// overallPuts externally and compare it to the size of the fpSet.
		// Additionally we assert that the fpSet has at least the seen
		// INSERTIONS elements and that maximally NUM_THREADS extra elements are
		// in the fpSet.
		assertEquals(overallPuts, fpSet.size());
		assertTrue(fpSet.size() >= INSERTIONS);
		assertTrue(fpSet.size() <= INSERTIONS + NUM_THREADS);
		
		// Check a DiskFPSet's invariant that after flush all fingerprints in
		// the file are a) monotonically increasing and b) there are no duplicates.
		if (fpSet instanceof DiskFPSet) {
			assertTrue(((DiskFPSet)fpSet).checkInvariant());
		}
	}
}
