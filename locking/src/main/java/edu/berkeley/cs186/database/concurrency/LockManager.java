package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.BaseTransaction;
import edu.berkeley.cs186.database.common.Pair;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LockManager maintains the bookkeeping for what transactions have
 * what locks on what resources. The lock manager should generally **not**
 * be used directly: instead, code should call methods of LockContext to
 * acquire/release/promote/escalate locks.
 *
 * The LockManager is primarily concerned with the mappings between
 * transactions, resources, and locks, and does not concern itself with
 * multiple levels of granularity (you can and should treat ResourceName
 * as a generic Object, rather than as an object encapsulating levels of
 * granularity, in this class).
 *
 * It follows that LockManager should allow **all**
 * requests that are valid from the perspective of treating every resource
 * as independent objects, even if they would be invalid from a
 * multigranularity locking perspective. For example, if LockManager#acquire
 * is called asking for an X lock on Table A, and the transaction has no
 * locks at the time, the request is considered valid (because the only problem
 * with such a request would be that the transaction does not have the appropriate
 * intent locks, but that is a multigranularity concern).
 */
public class LockManager {
    // These members are given as a suggestion. You are not required to use them, and may
    // delete them and add members as you see fit.
    private Map<Long, List<Lock>> transactionLocks = new HashMap<>();
    private Map<ResourceName, List<Pair<Long, Lock>>> resourceLocks = new HashMap<>();
    private Deque<LockRequest> waitingQueue = new ArrayDeque<>();

    // You should not modify this.
    protected Map<Object, LockContext> contexts = new HashMap<>();

    public LockManager() {}

    /**
     * Create a lock context for the database. See comments at
     * the top of this file and the top of LockContext.java for more information.
     */
    public LockContext databaseContext() {
        if (!contexts.containsKey("database")) {
            contexts.put("database", new LockContext(this, null, "database"));
        }
        return contexts.get("database");
    }

    /**
     * Create a lock context with no parent. Cannot be called "database".
     */
    public LockContext orphanContext(Object name) {
        if (name.equals("database")) {
            throw new IllegalArgumentException("cannot create orphan context named 'database'");
        }
        if (!contexts.containsKey(name)) {
            contexts.put(name, new LockContext(this, null, name));
        }
        return contexts.get(name);
    }

    /**
     * Acquire a LOCKTYPE lock on NAME, for transaction TRANSACTION, and releases all locks
     * in RELEASELOCKS after acquiring the lock. No error checking is performed for holding
     * requisite parent locks or freeing dependent child locks. Blocks the transaction and
     * places it in queue if the requested lock is not compatible with another transaction's
     * lock on the resource. Unblocks and unqueues all transactions that can be unblocked
     * after releasing locks in RELEASELOCKS, in order of lock request.
     *
     * @throws DuplicateLockRequestException if a lock on NAME is held by TRANSACTION and
     * isn't being released
     * @throws NoLockHeldException if no lock on a name in RELEASELOCKS is held by TRANSACTION
     */
    public void acquireAndRelease(BaseTransaction transaction, ResourceName name,
                                  LockType lockType, List<ResourceName> releaseLocks)
    throws DuplicateLockRequestException, NoLockHeldException {
        // TODO TODO FUCK FUCK CHECK IF RELEASELOCKS ARE ACTUALLY HELD
        releaseLocks = new ArrayList<>(releaseLocks);
        Lock l = new Lock(name, lockType);
        long tn = transaction.getTransNum();
        List<Pair<Long, Lock>> rLocks = resourceLocks.get(name);

        if (rLocks != null) {
            if (rLocks.stream().anyMatch(p -> p.getFirst() != tn && !LockType.compatible(p.getSecond().lockType, lockType))) {
                transaction.block();
                List<Lock> ihatethis = releaseLocks.stream().map(rn -> new Lock(rn, null)).collect(Collectors.toList());
                waitingQueue.add(new LockRequest(transaction, l, ihatethis));
                return;
            }

            List<Pair<Long, Lock>> alreadyOwn = rLocks.stream().filter(p -> p.getFirst() == tn).collect(Collectors.toList());
            if (alreadyOwn.size() > 0) {
                // System.out.println("FUCKFUCKFUCK");
                if (releaseLocks.stream().noneMatch(rn -> rn == name)) {
                    throw new DuplicateLockRequestException("walla walla bing bong");
                }
                // swap out
                // IN PLACE
                inPlaceSwap(transaction, name, lockType);
                releaseLocks.remove(name);
                releaseLocks.forEach(rn -> release(transaction, rn));
                return;
            }

            rLocks.add(new Pair<>(tn, l));
            resourceLocks.put(name, rLocks);
        } else {
            List<Pair<Long, Lock>> newList = new ArrayList<>();
            newList.add(new Pair<>(tn, l));
            resourceLocks.put(name, newList);
        }

        if (transactionLocks.containsKey(tn)) {
            List<Lock> locks = transactionLocks.get(tn);
            locks.add(l);
            transactionLocks.put(tn, locks);
        } else {
            List<Lock> newList2 = new ArrayList<>();
            newList2.add(l);
            transactionLocks.put(tn, newList2);
        }

        releaseLocks.forEach(rn -> release(transaction, rn));
    }

    /**
     * Acquire a LOCKTYPE lock on NAME, for transaction TRANSACTION. No error
     * checking is performed for holding requisite parent locks. Blocks the
     * transaction and places it in queue if the requested lock is not compatible
     * with another transaction's lock on the resource.
     *
     * @throws DuplicateLockRequestException if a lock on NAME is held by
     * TRANSACTION
     */
    public void acquire(BaseTransaction transaction, ResourceName name,
                        LockType lockType) throws DuplicateLockRequestException {
        Lock l = new Lock(name, lockType);
        long tn = transaction.getTransNum();
        List<Pair<Long, Lock>> rLocks = resourceLocks.get(name);

        if (rLocks != null) {
            for (Pair<Long, Lock> p : rLocks) {
                if (p.getFirst() == tn) { // TODO OR DO THIS VIA XACT_LOCKS
                    throw new DuplicateLockRequestException("walla walla bing bong");
                }
                if (p.getFirst() != tn && !LockType.compatible(p.getSecond().lockType, lockType)) {
                    transaction.block();
                    waitingQueue.add(new LockRequest(transaction, l));
                    return;
                }
            }
            rLocks.add(new Pair<>(tn, l));
            resourceLocks.put(name, rLocks);
        } else {
            List<Pair<Long, Lock>> newList = new ArrayList<>();
            newList.add(new Pair<>(tn, l));
            resourceLocks.put(name, newList);
        }

        if (transactionLocks.containsKey(tn)) {
            List<Lock> locks = transactionLocks.get(tn);
            locks.add(l);
            transactionLocks.put(tn, locks);
        } else {
            List<Lock> newList2 = new ArrayList<>();
            newList2.add(l);
            transactionLocks.put(tn, newList2);
        }
    }

    /**
     * Release TRANSACTION's lock on NAME. No error checking is performed for
     * freeing dependent child locks. Unblocks and unqueues all transactions
     * that can be unblocked, in order of lock request.
     *
     * @throws NoLockHeldException if no lock on NAME is held by TRANSACTION
     */
    public void release(BaseTransaction transaction, ResourceName name)
    throws NoLockHeldException {
        long tn = transaction.getTransNum();
        List<Lock> locks = transactionLocks.get(tn);
        if (locks == null || locks.stream().noneMatch(lock -> lock.name == name)) {
            throw new NoLockHeldException("highway to hickory hole");
        }
        locks = locks.stream()
                .filter(lock -> lock.name != name)
                .collect(Collectors.toList());
        transactionLocks.put(tn, locks);

        List<Pair<Long, Lock>> rLocks = resourceLocks.get(name);
        // TODO DO WE NEED CHECK HERE
        rLocks = rLocks.stream()
                .filter(pair -> pair.getFirst() != tn)
                .collect(Collectors.toList());
        resourceLocks.put(name, rLocks);

        Deque<LockRequest> newQueue = new ArrayDeque<>(waitingQueue);
        waitingQueue = new ArrayDeque<>();

        for (LockRequest req : newQueue) {
            if (req.lock.name == name) {
                // retry request
                List<ResourceName> releasedNames = req.releasedLocks.stream()
                                                                    .map(lock -> lock.name)
                                                                    .collect(Collectors.toList());
                req.transaction.unblock();
                acquireAndRelease(req.transaction, req.lock.name, req.lock.lockType, releasedNames);
                // TODO find it in waiting queue and add req.releasedLocks?
            } else {
                waitingQueue.add(req);
            }
        }
    }

    /**
     * Promote TRANSACTION's lock on NAME to NEWLOCKTYPE. No error checking is
     * performed for holding requisite locks. Blocks the transaction and places
     * TRANSACTION in the **front** of the queue if the request cannot be
     * immediately granted (i.e. another transaction holds a conflicting lock). A
     * lock promotion **should not** change the acquisition time of the lock, i.e. TODO SAME WITH DEMOTION
     * if a transaction acquired locks in the order: S(A), X(B), promote X(A), the
     * lock on A is considered to have been acquired before the lock on B.
     *
     * @throws DuplicateLockRequestException if TRANSACTION already has a
     * NEWLOCKTYPE lock on NAME
     * @throws NoLockHeldException if TRANSACTION has no lock on NAME
     * @throws InvalidLockException if the requested lock type is not a promotion. A promotion
     * from lock type A to lock type B is valid if and only if B is substitutable
     * for A, and B is not equal to A.
     */
    public void promote(BaseTransaction transaction, ResourceName name,
                        LockType newLockType)
    throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {

        long tn = transaction.getTransNum();
        List<Lock> tLocks = transactionLocks.get(tn);

        if (tLocks == null) {
            throw new NoLockHeldException("culinary anthropology that's endemic to");
        }
        List<Lock> match = tLocks.stream().filter(l -> l.name == name).collect(Collectors.toList());
        if (match.isEmpty()) {
            throw new NoLockHeldException("culinary anthropology that's endemic to");
        } else if (match.get(0).lockType == newLockType) {
            throw new DuplicateLockRequestException("feast of sound");
        } else if (!LockType.substitutable(newLockType, match.get(0).lockType)) {
            throw new InvalidLockException("swingin the sickle with both hands");
        }

        inPlaceSwap(transaction, name, newLockType);
    }

    private void inPlaceSwap(BaseTransaction transaction, ResourceName name, LockType newLockType) {
        List<Pair<Long, Lock>> rLocks = resourceLocks.get(name);
        long tn = transaction.getTransNum();

        if (rLocks.stream().anyMatch(p -> p.getFirst() != tn && !LockType.compatible(p.getSecond().lockType, newLockType))) {
            transaction.block();
            List<Lock> bs = new ArrayList<>();
            bs.add(new Lock(name, null));
            waitingQueue.addFirst(new LockRequest(transaction, new Lock(name, newLockType), bs));
            return;
        }

        int index = 0;
        for (int i = 0; i < rLocks.size(); i++) {
            if (rLocks.get(i).getFirst() == tn) {
                index = i;
            }
        }
        rLocks.remove(index);
        rLocks.add(index, new Pair<>(tn, new Lock(name, newLockType)));

        List<Lock> tLocks = transactionLocks.get(tn);

        index = 0;
        for (int i = 0; i < tLocks.size(); i++) {
            if (tLocks.get(i).name == name) {
                index = i;
            }
        }
        tLocks.remove(index);
        tLocks.add(index, new Lock(name, newLockType));
    }

    /**
     * Return the type of lock TRANSACTION has on NAME, or null if no lock is
     * held.
     */
    public LockType getLockType(BaseTransaction transaction, ResourceName name) {
        List<Lock> locks = transactionLocks.get(transaction.getTransNum());
        if (locks == null) {
            return null;
        }
        locks = locks.stream().filter(lock -> lock.name == name).collect(Collectors.toList());
        return locks.isEmpty() ? null : locks.get(0).lockType;
    }

    /**
     * Returns the list of transactions ids and lock types for locks held on
     * NAME, in order of acquisition. A promotion should count as acquired
     * at the original time.
     */
    public List<Pair<Long, LockType>> getLocks(ResourceName name) {
        List<Pair<Long, Lock>> rLocks = resourceLocks.get(name);
        if (rLocks == null) {
            return new ArrayList<>();
        }
        return rLocks.stream()
                .map(pair -> new Pair<>(pair.getFirst(), pair.getSecond().lockType))
                .collect(Collectors.toList());
    }

    /**
     * Returns the list of resource names and lock types for locks held by
     * TRANSACTION, in order of acquisition. A promotion should count as acquired
     * at the original time.
     */
    public List<Pair<ResourceName, LockType>> getLocks(BaseTransaction transaction) {
        List<Lock> locks = transactionLocks.get(transaction.getTransNum());
        if (locks == null) {
            return new ArrayList<>();
        }
        return locks.stream()
                .map(lock -> new Pair<>(lock.name, lock.lockType))
                .collect(Collectors.toList());
    }
}
