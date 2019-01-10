package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.BaseTransaction;
import edu.berkeley.cs186.database.common.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static edu.berkeley.cs186.database.concurrency.LockType.*;

public class LockUtil {
    /**
     * Ensure that TRANSACTION can perform actions requiring LOCKTYPE on LOCKCONTEXT.
     * This method should promote/escalate as needed, but should only grant the least
     * permissive set of locks needed.
     *
     * lockType must be one of LockType.S, LockType.X, and behavior is unspecified
     * if an intent lock is passed in to this method (you can do whatever you want in this case).
     *
     * If TRANSACTION is null, this method should do nothing.
     */
    public static void requestLocks(BaseTransaction transaction, LockContext lockContext,
                                    LockType lockType) {
        if (transaction == null || lockContext == null || lockType == null || !(lockType == S || lockType == X)) {
            return;
        }

        // check current
        List<Pair<ResourceName, LockType>> allLocks = lockContext.lockman.getLocks(transaction);
        List<Pair<ResourceName, LockType>> currLocks = allLocks.stream()
                .filter(p -> p.getFirst() == lockContext.name).collect(Collectors.toList());
        if (currLocks.size() > 0) { // has a current lock
            LockType currType = currLocks.get(0).getSecond();
            if (currType == lockType) { // already has the desired lock
                return;
            } else if (lockType == S && (currType == X || currType == SIX)) { // has better lock
                return;
            } else { // need to promote
                // promote parents if needed
                List<LockContext> topDown = new ArrayList<>();
                LockContext lc = lockContext.parent;
                while (lc != null) {
                    topDown.add(0, lc);
                    lc = lc.parent;
                }

                for (LockContext walk : topDown) {
                    ResourceName FUCK = walk.name;
                    List<Pair<ResourceName, LockType>> walkLocks = allLocks.stream()
                            .filter(p -> p.getFirst() == FUCK).collect(Collectors.toList());

                    LockType walkType = walkLocks.get(0).getSecond();
                    if (lockType == X) {
                        switch (walkType) {
                            case S: // S -> SIX
                                walk.promote(transaction, SIX);
                                break;
                            case IS: // IS -> IX
                                walk.promote(transaction, IX);
                                break;
                            default:
                                break;
                        }
                    }
                }

                // escalate if needed TODO MULTIPLE LEVELS OF CHILDREN FUCK
                if (lockContext.numChildLocks.get(transaction.getTransNum()) != null) {
                    lockContext.escalate(transaction);
                    allLocks = lockContext.lockman.getLocks(transaction);
                    currLocks = allLocks.stream()
                            .filter(p -> p.getFirst() == lockContext.name).collect(Collectors.toList());
                    currType = currLocks.get(0).getSecond();
                }

                // promote current if needed
                if (lockType == S && (currType == IS || currType == IX)) {
                    lockContext.release(transaction);
                    lockContext.acquire(transaction, S);
                } else if (lockType == X && currType != X) {
                    lockContext.promote(transaction, X);
                }
            }
        } else { // no current lock
            // promote / acquire parents if needed
            List<LockContext> topDown = new ArrayList<>();
            LockContext lc = lockContext.parent;
            while (lc != null) {
                topDown.add(0, lc);
                lc = lc.parent;
            }

            for (LockContext walk : topDown) {
                ResourceName FUCK = walk.name;
                List<Pair<ResourceName, LockType>> walkLocks = allLocks.stream()
                        .filter(p -> p.getFirst() == FUCK).collect(Collectors.toList());

                if (walkLocks.isEmpty()) {
                    // acquire
                    walk.acquire(transaction, lockType == S ? IS : IX);
                } else {
                    // promote
                    LockType walkType = walkLocks.get(0).getSecond();
                    if (lockType == S && (walkType == S || walkType == X || walkType == SIX)) {
                        return;
                    }
                    if (lockType == X) {
                        switch (walkType) {
                            case X:
                                return;
                            case S: // S -> SIX
                                walk.promote(transaction, SIX);
                                break;
                            case IS: // IS -> IX
                                walk.promote(transaction, IX);
                                break;
                            default:
                                break;
                        }
                    }
                }
            }

            // acquire current
            lockContext.acquire(transaction, lockType);
        }


        // if transaction has children locks, then escalate from the bottom and promote if necessary
        // promotion includes parent promotion

        // otherwise, if transaction has current lock, promote if necessary
        // promotion includes parent promotion

        // otherwise acquire
        // includes parent acquire / promotion
    }

    // TODO(hw5): add helper methods as you see fit
}
