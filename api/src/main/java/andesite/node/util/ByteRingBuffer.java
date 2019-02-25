package andesite.node.util;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Circular buffer of bytes. When the buffer is full, adding a new element
 * removes the oldest. Insertion and random access are O(1).
 */
public class ByteRingBuffer implements Iterable<Byte> {
    private final byte[] array;
    private int position;
    private int size;

    public ByteRingBuffer(int size) {
        if(size < 1) {
            throw new IllegalArgumentException("Size < 1");
        }
        this.array = new byte[size];
    }
    
    /**
     * Stores a value into this buffer. If the buffer is full, the
     * oldest value is removed.
     *
     * @param value Value to store.
     */
    public void put(byte value) {
        array[position] = value;
        position = (position + 1) % array.length;
        size = Math.min(size + 1, array.length);
    }
    
    /**
     * Clears this buffer.
     */
    public void clear() {
        position = 0;
        size = 0;
        Arrays.fill(array, (byte)0);
    }
    
    /**
     * Returns the sum of all values on this buffer.
     *
     * @return The sum of all values on this buffer.
     */
    public int sum() {
        //safe to sum all values because the array is zeroed in clear()
        var sum = 0;
        for(var v : array) {
            sum += v;
        }
        return sum;
    }
    
    /**
     * Returns the size of this buffer.
     *
     * @return The size of this buffer.
     */
    public int size() {
        return size;
    }
    
    /**
     * Returns the {@code n}th element of this buffer. An index of 0
     * returns the oldest, an index of {@code size() - 1} returns the
     * newest.
     *
     * @param n Index of the wanted element.
     *
     * @return The value of the {@code n}th element.
     *
     * @throws NoSuchElementException If {@code n >= size()}.
     */
    public byte get(int n) {
        if(n >= size) {
            throw new NoSuchElementException();
        }
        return array[sub(position, size - n, array.length)];
    }
    
    /**
     * Returns the last element of this buffer. Equivalent to
     * {@code getLast(0)}.
     *
     * @return The last element.
     *
     * @throws NoSuchElementException If this buffer is empty.
     */
    public byte getLast() {
        return getLast(0);
    }
    
    /**
     * Returns the {@code n}th last element of this buffer. An
     * index of 0 returns the newest, an index of {@code size() - 1}
     * returns the oldest.
     *
     * @param n Index of the wanted element.
     *
     * @return  The value of the {@code n}th last element.
     *
     * @throws NoSuchElementException If {@code n >= size()}.
     */
    public byte getLast(int n) {
        if(n >= size) {
            throw new NoSuchElementException();
        }
        return array[sub(position, n + 1, array.length)];
    }

    private static int sub(int i, int j, int modulus) {
        if ((i -= j) < 0) i += modulus;
        return i;
    }

    private  static int inc(int i, int modulus) {
        if (++i >= modulus) i = 0;
        return i;
    }

    @Nonnull
    @Override
    public Iterator<Byte> iterator() {
        return new Iterator<>() {
            private int cursor = position;
            private int remaining = size;

            @Override
            public boolean hasNext() {
                return remaining > 0;
            }

            @Override
            public Byte next() {
                var v = array[cursor];
                cursor = inc(cursor, array.length);
                remaining--;
                return v;
            }
        };
    }
}
