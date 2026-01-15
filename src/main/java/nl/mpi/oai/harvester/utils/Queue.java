package nl.mpi.oai.harvester.utils;

import java.util.LinkedList;

public class Queue<T> {
    private LinkedList<T> list = new LinkedList<T>();

    // Add element to the end of the queue
    public void enqueue(T item) {
        list.addLast(item);
    }

    // Remove element from the front of the queue
    public T dequeue() {
        if (list.isEmpty()) {
            return null;
        }
        return list.removeFirst();
    }

    // Is the queue empty?
    public boolean isEmpty() {
        return list.isEmpty();
    }

    // Get the size of the queue
    public int size() {
        return list.size();
    }

    // Peek at the front of the queue without removing it
    public T peek() {
        if (list.isEmpty()) {
            return null;
        }
        return list.getFirst();
    }
}
