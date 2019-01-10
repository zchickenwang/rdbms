package edu.berkeley.cs186.database.concurrency;

import java.util.Arrays;
import java.util.List;

public enum LockType {
    S,   // shared
    X,   // exclusive
    IS,  // intention shared
    IX,  // intention exclusive
    SIX; // shared intention exclusive

    /**
     * This method checks whether lock types A and B are compatible with
     * each other. If a transaction can hold lock type A on a resource
     * at the same time another transaction holds lock type B on the same
     * resource, the lock types are compatible. A null represents no lock.
     */
    public static boolean compatible(LockType a, LockType b) {
        if (a == null || b == null) {
            return true;
        }

        switch (a) {
            case S:
                return (b == IS || b == S);
            case X:
                return false;
            case IS:
                return (b != X);
            case IX:
                return (b == IS || b == IX);
            case SIX:
                return (b == IS);
            default:
                return false;
        }
    }

    /**
     * This method returns the least permissive lock on the parent resource
     * that must be held for a lock of type A to be granted. A null
     * represents no lock.
     */
    public static LockType parentLock(LockType a) {
        if (a == null) {
            return null;
        }

        switch (a) {
            case S:
                return IS;
            case X:
                return IX;
            case IS:
                return IS;
            case IX:
                return IX;
            case SIX:
                return IX;
            default:
                return null;
        }
    }

    /**
     * This method returns whether a lock can be used for a situation
     * requiring another lock (e.g. an S lock can be substituted with
     * an X lock, because an X lock allows the transaction to do everything
     * the S lock allowed it to do). A null represents no lock.
     */
    public static boolean substitutable(LockType substitute, LockType required) {
        if (required == null) {
            return true;
        }
        if (substitute == null) {
            return false;
        }
        if (substitute == required) {
            return true;
        }

        switch (required) {
            case S:
                return (substitute != IS && substitute != IX);
            case X:
                return (substitute == X);
            case IS:
                return true; // TODO FUCKING FUCK
            case IX:
                return (substitute == SIX || substitute == X);
            case SIX:
                return (substitute == SIX || substitute == X);
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        switch (this) {
        case S: return "S";
        case X: return "X";
        case IS: return "IS";
        case IX: return "IX";
        case SIX: return "SIX";
        default: throw new UnsupportedOperationException("bad lock type");
        }
    }
};

