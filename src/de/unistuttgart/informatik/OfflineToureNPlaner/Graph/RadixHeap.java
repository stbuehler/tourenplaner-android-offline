package de.unistuttgart.informatik.OfflineToureNPlaner.Graph;

import java.util.Arrays;
import com.carrotsearch.hppc.ArraySizingStrategy;

/**
 * provides (one-level) radix heap structures for a minimum heap for ints
 *   the inserted keys must not be smaller than the last "popped" element
 *
 * If the minimum key (or the last popped key) is less than 0, all positive keys
 * get sorted into the highest bucket. That is why the default minimum key is 0;
 * choose an Integer.MIN_VALUE if you need negative keys.
 *
 * @author Stefan Bühler
 *
 * Literature: Ahuja, K., K. Melhorn, J.B. Orlin, and R.E. Tarjan, Faster algorithms for the shortest path problem, Jour. ACM, 37 (1990) 213-223.
 *
 */
public final class RadixHeap {
	private final static int BUCKETS = 33; /* bit width of int + 1 */

	private final int[][] bucketarray;
	private final int[] bucketentries; // each bucket entry takes up two slots in the array
	private int bucketNonEmpty; // bit vector. bit 2^(31-i) set means bucket i is non empty. bucket 32 is not stored.

	private int heapentries;

	private int last_key, last_value;

	/** the {@link #resizer} is applied to the actual number of ints in the array, not
	 * the number of entries in the heap.
	 */
	private final ArraySizingStrategy resizer;

	public static final int DEFAULT_MINIMUM_KEY = 0;

	private static final int LARGE_INITIAL_BUCKET_SIZE = 1024; /* if a bucket is empty, start with LARGE_INITIAL_BUCKET_SIZE entries */
	private static final int[] INITIAL_BUCKET_SIZES = {
		16,
		16, 32, 32, 32, 32, 64, 64, 64,
		128, 128, 128, 256, 256, 512, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
	};

	/**
	 * Uses {@value #DEFAULT_MINIMUM_KEY} as minimum key.
	 */
	public RadixHeap() {
		this(0);
	}

	/**
	 * initializes a heap with given minimum key
	 */
	public RadixHeap(int minimumKey) {
		this(minimumKey, new com.carrotsearch.hppc.BoundedProportionalArraySizingStrategy());
	}

	/**
	 * initializes a heap with given minimum key and a custom resizing strategy
	 */
	public RadixHeap(int minimumKey, ArraySizingStrategy resizer) {
		assert resizer != null;
		this.resizer = resizer;
		bucketarray = new int[BUCKETS][];
		for (int i = 0; i < BUCKETS; ++i) bucketarray[i] = new int[INITIAL_BUCKET_SIZES[i]];
		bucketentries = new int[BUCKETS];
		heapentries = 0;
		last_key = minimumKey;
		last_value = Integer.MIN_VALUE;
		bucketNonEmpty = 0;
	}

	/*
	private final int bucket(int v) {
		final int b = 32 - Integer.numberOfLeadingZeros(v ^ last_key);
		assert 0 <= b && b <= BUCKETS;
		return b;
	}
	*/

	/*
	private final void set_bucket_non_empty(int b) {
		if (b >= 32) return;
		assert b >= 0;
		final int mask = (Integer.MIN_VALUE >>> b);
		bucketNonEmpty |= mask;
	}
	*/

	/*
	private final void set_bucket_empty(int b) {
		if (b >= 32) return;
		assert b >= 0;
		final int mask = (Integer.MIN_VALUE >>> b);
		bucketNonEmpty &= ~mask;
	}
	*/

	/*
	private final int first_possibly_non_empty_bucket() {
		return Integer.numberOfLeadingZeros(bucketNonEmpty);
	}
	*/

	private final void bucket_insert(int b, int key, int value) {
		/* set_bucket_non_empty(b); */
		if (b < 32) {
			assert b >= 0;
			final int mask = (Integer.MIN_VALUE >>> b);
			bucketNonEmpty |= mask;
		}

		final int entryNdx = (bucketentries[b]++) * 2;
		final int[] ba = bucketarray[b];
		if (entryNdx + 1 < ba.length) { 
			ba[entryNdx] = key;
			ba[entryNdx + 1] = value;
		} else {
			ensureBucketSpace(b);
			final int[] nba = bucketarray[b];
			nba[entryNdx] = key;
			nba[entryNdx + 1] = value;
		}
	}

	/**
	 * insert a element to the heap
	 *
	 * @param key to sort value by
	 * @param value
	 */
	public final void insert(int key, int value) {
		assert key >= last_key;
		final int b = 32 - Integer.numberOfLeadingZeros(key ^ last_key);
		bucket_insert(b, key, value);
		++heapentries;
	}

	/**
	 * update an element in the heap (or inserts it if the (key, value) can't be found). "slow" (linear search through bucket).
	 *
	 * @param oldkey
	 * @param key to sort value by
	 * @param value
	 */
	public final void update(int oldkey, int key, int value) {
		assert key >= last_key;
		final int ob = 32 - Integer.numberOfLeadingZeros(oldkey ^ last_key);
		final int b = 32 - Integer.numberOfLeadingZeros(key ^ last_key);

		int obe = 2*bucketentries[ob];
		final int[] oba = bucketarray[ob];

		for (int i = 0; i < obe; i += 2) {
			if (oba[i] == oldkey && oba[i+1] == value) {
				if (ob != b) {
					/* move value to new bucket, first remove in old bucket: replace by last element in old bucket */
					obe -= 2;
					if (i != obe) {
						oba[i] = oba[obe];
						oba[i+1] = oba[obe+1];
					}
					--bucketentries[ob];
					if (0 == obe && ob < 32) {
						// set_bucket_empty(ob); /* now empty */
						final int mask = (Integer.MIN_VALUE >>> ob);
						bucketNonEmpty &= ~mask;
					}
					/* now insert in new bucket */
					bucket_insert(b, key, value);
				} else {
					/* same bucket, just update key */
					oba[i] = key;
				}
				return;
			}
		}

		/* not found, just insert */
		bucket_insert(b, key, value);
		++heapentries;
	}


	/**
	 * check for empty heap
	 *
	 * @return
	 */
	public final boolean isEmpty() {
		return heapentries <= 0;
	}

	/**
	 * returns the key of the last extracted element
	 *
	 * @return
	 */
	public final int lastKey() {
		return last_key;
	}

	/**
	 * returns the value of the last extracted element
	 *
	 * @return
	 */
	public final int lastValue() {
		return last_value;
	}

	/**
	 * removes the minimum element of the heap, and returns its value
	 */
	public final int extract() {
		assert heapentries > 0;
		--heapentries;
		int b;
		for (b = Integer.numberOfLeadingZeros(bucketNonEmpty) /* first_possibly_non_empty_bucket() */; b < BUCKETS; ++b) {
			if (0 != bucketentries[b]) break;
		}
		assert b < BUCKETS; /* at least one non empty bucket must exist */

		final int[] ba = bucketarray[b];

		if (0 == b) {
			/* all keys in bucket 0 are equal to last_key, pick last entry */
			final int idx = 2*(--bucketentries[0]);
			assert last_key == ba[idx];
			last_value = ba[idx+1];
			if (0 == idx) {
				// set_bucket_empty(b); /* now empty */
				bucketNonEmpty &= Integer.MAX_VALUE; // Integer.MAX_VALUE == ~(Integer.MIN_VALUE >>> 0)
			}
			return last_value;
		}

		/* find minimum element in bucket */
		int be = 2*bucketentries[b];
		int minkey = ba[0], minidx = 0;

		for (int i = 2; i < be; i += 2) {
			final int k = ba[i];
			if (minkey > k) {
				minidx = i;
				minkey = k;
			}
		}

		/* extract minimum element */
		last_key = minkey;
		last_value = ba[minidx+1];

		/* remove element at minidx: replace by last element */
		be -= 2;
		if (minidx != be) {
			ba[minidx] = ba[be];
			ba[minidx+1] = ba[be+1];
		}

		/* buckets below b are empty, buckets above b don't change */
		/* redistribute bucket b */
		assert b != 0; /* b == 0 handled above */
		for (int i = 0; i < be; i += 2) {
			final int rkey = ba[i], rvalue = ba[i+1];
			final int nb = 32 - Integer.numberOfLeadingZeros(rkey ^ minkey); // bucket(ba[i]);
			assert nb < b; /* all keys in bucket b have the same bits above bit (b-1) as last_key, so nb <= b-1 < b */
			bucket_insert(nb, rkey, rvalue);
		}
		bucketentries[b] = 0; /* all keys were distributed to new (smaller) buckets */
		//set_bucket_empty(b); /* now empty */
		if (b < 32) {
			final int mask = (Integer.MIN_VALUE >>> b);
			bucketNonEmpty &= ~mask;
		}

		return last_value;
	}

	/**
	 * resets heap
	 */
	public final void resetHeap(int minimumKey) {
		Arrays.fill(bucketentries, 0);
		heapentries = 0;
		last_key = minimumKey;
		last_value = Integer.MIN_VALUE;
		bucketNonEmpty = 0;
	}

	/**
	 * resets heap with {@value #DEFAULT_MINIMUM_KEY} as minimum key
	 */
	public final void resetHeap() {
		resetHeap(DEFAULT_MINIMUM_KEY);
	}

	/* always calls resizer#grow, only call if you really need more space */
	private final void ensureBucketSpace(int b) {
		final int[] ba = bucketarray[b];
		final int have = ba.length;
		if (0 == have) {
			bucketarray[b] = new int[LARGE_INITIAL_BUCKET_SIZE];
		} else {
			final int newLen = resizer.grow(have, bucketentries[b] * 2, 2);
			bucketarray[b] = Arrays.copyOf(ba, newLen);
		}
	}
}
