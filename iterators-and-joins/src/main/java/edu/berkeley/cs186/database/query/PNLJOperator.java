package edu.berkeley.cs186.database.query;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.common.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.io.Page;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.RecordIterator;
import edu.berkeley.cs186.database.table.Schema;

public class PNLJOperator extends JoinOperator {

  public PNLJOperator(QueryOperator leftSource,
                      QueryOperator rightSource,
                      String leftColumnName,
                      String rightColumnName,
                      Database.Transaction transaction) throws QueryPlanException, DatabaseException {
    super(leftSource,
          rightSource,
          leftColumnName,
          rightColumnName,
          transaction,
          JoinType.PNLJ);

  }

  public Iterator<Record> iterator() throws QueryPlanException, DatabaseException {
    return new PNLJIterator();
  }


  public int estimateIOCost() throws QueryPlanException {
	    //does nothing
	    return 0;
  }

  /**
   * An implementation of Iterator that provides an iterator interface for this operator.
   */
  private class PNLJIterator extends JoinIterator {
    /**
     * Some member variables are provided for guidance, but there are many possible solutions.
     * You should implement the solution that's best for you, using any member variables you need.
     * You're free to use these member variables, but you're not obligated to.
     */

    private Iterator<Page> leftIterator = null;
    private Iterator<Page> rightIterator = null;
    private BacktrackingIterator<Record> leftRecordIterator = null;
    private BacktrackingIterator<Record> rightRecordIterator = null;
    private Record leftRecord = null;
    private Record rightRecord = null;
    private Record nextRecord = null;

    public PNLJIterator() throws QueryPlanException, DatabaseException {
      super();

      this.leftIterator = PNLJOperator.this.getPageIterator(this.getLeftTableName());
      this.rightIterator = PNLJOperator.this.getPageIterator(this.getRightTableName());
      if (!this.leftIterator.hasNext() || !this.rightIterator.hasNext()) {
          // TODO NULL CHECKS
          return;
      }
      this.leftIterator.next();
      this.rightIterator.next();
      if (!this.leftIterator.hasNext() || !this.rightIterator.hasNext()) {
          // TODO NULL CHECKS
          return;
      }

      this.leftRecordIterator = PNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftIterator, 1);
      this.rightRecordIterator = PNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);

      this.leftRecord = this.leftRecordIterator.hasNext() ? this.leftRecordIterator.next() : null;
      this.rightRecord = this.rightRecordIterator.hasNext() ? this.rightRecordIterator.next() : null;

      if (rightRecordIterator != null) {
          rightRecordIterator.mark();
      } else return;
      if (leftRecordIterator != null) {
          leftRecordIterator.mark();
      } else return;

      try {
          fetchNextRecord();
      } catch (DatabaseException e) {
          this.nextRecord = null;
      }
    }

    private void fetchNextRecord() throws DatabaseException {
        this.nextRecord = null;
        do {
            if (this.rightRecord != null) {
                DataBox leftJoinValue = this.leftRecord.getValues().get(PNLJOperator.this.getLeftColumnIndex());
                DataBox rightJoinValue = this.rightRecord.getValues().get(PNLJOperator.this.getRightColumnIndex());
                if (leftJoinValue.equals(rightJoinValue)) {
                    List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
                    List<DataBox> rightValues = new ArrayList<>(this.rightRecord.getValues());
                    leftValues.addAll(rightValues);
                    this.nextRecord = new Record(leftValues);
                }
                this.rightRecord = rightRecordIterator.hasNext() ? rightRecordIterator.next() : null;
            }
            else {
                // reset right record
                this.rightRecordIterator.reset();
                assert(this.rightRecordIterator.hasNext());
                this.rightRecord = this.rightRecordIterator.next();
                this.rightRecordIterator.mark();

                if (this.leftRecordIterator.hasNext()) {
                    // get next left record
                    this.leftRecord = this.leftRecordIterator.next();
                }

                // if next left record is null
                else {
                    // reset left record
                    this.leftRecordIterator.reset();
                    assert(this.leftRecordIterator.hasNext());
                    this.leftRecord = this.leftRecordIterator.next();
                    this.leftRecordIterator.mark();
                    boolean FUCK = true;
                    while (this.rightIterator.hasNext()) {
                        // get next right page
                        this.rightRecordIterator = PNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);
                        this.rightRecord = this.rightRecordIterator.hasNext() ? this.rightRecordIterator.next() : null;
                        if (this.rightRecord != null) {
                            this.rightRecordIterator.mark();
                            FUCK = false;
                            break;
                        }
                    }

                    // if next right page is null
                    if (FUCK && !this.rightIterator.hasNext()) {
                        // reset right page
                        this.rightIterator = PNLJOperator.this.getPageIterator(this.getRightTableName());
                        this.rightIterator.next();
                        this.rightRecordIterator = PNLJOperator.this.getBlockIterator(this.getRightTableName(), this.rightIterator, 1);
                        this.rightRecord = this.rightRecordIterator.hasNext() ? this.rightRecordIterator.next() : null;
                        if (rightRecordIterator != null) {
                            rightRecordIterator.mark();
                        }
                        boolean FUCKFUCK = true;
                        // get next left page
                        while (this.leftIterator.hasNext()) {
                            this.leftRecordIterator = PNLJOperator.this.getBlockIterator(this.getLeftTableName(), this.leftIterator, 1);
                            this.leftRecord = this.leftRecordIterator.hasNext() ? this.leftRecordIterator.next() : null;
                            if (this.leftRecord != null) {
                                this.leftRecordIterator.mark();
                                FUCKFUCK = false;
                                break;
                            }
                        }

                        // if next left page is null
                        if (FUCKFUCK && !this.leftIterator.hasNext()) {
                            // we are done
                            throw new DatabaseException("All done bitches!");
                        }
                    }
                }
            }
        } while (!hasNext());
    }

    /**
     * Checks if there are more record(s) to yield
     *
     * @return true if this iterator has another record to yield, otherwise false
     */
    public boolean hasNext() {
        return this.nextRecord != null;
      }

    /**
     * Yields the next record of this iterator.
     *
     * @return the next Record
     * @throws NoSuchElementException if there are no more Records to yield
     */
    public Record next() {
        if (!this.hasNext()) {
            throw new NoSuchElementException();
        }

        Record nextRecord = this.nextRecord;
        try {
            this.fetchNextRecord();
        } catch (DatabaseException e) {
            this.nextRecord = null;
        }
        return nextRecord;
    }

    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}

