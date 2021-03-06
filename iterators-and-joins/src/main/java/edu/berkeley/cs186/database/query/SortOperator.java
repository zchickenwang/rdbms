package edu.berkeley.cs186.database.query;

import edu.berkeley.cs186.database.Database;
import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.RecordIterator;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.common.Pair;
import edu.berkeley.cs186.database.io.Page;

import java.util.*;


public class SortOperator  {
  private Database.Transaction transaction;
  private String tableName;
  private Comparator<Record> comparator;
  private Schema operatorSchema;
  private int numBuffers;

  public SortOperator(Database.Transaction transaction, String tableName, Comparator<Record> comparator) throws DatabaseException, QueryPlanException {
    this.transaction = transaction;
    this.tableName = tableName;
    this.comparator = comparator;
    this.operatorSchema = this.computeSchema();
    this.numBuffers = this.transaction.getNumMemoryPages();
  }

  public Schema computeSchema() throws QueryPlanException {
    try {
      return this.transaction.getFullyQualifiedSchema(this.tableName);
    } catch (DatabaseException de) {
      throw new QueryPlanException(de);
    }
  }


  public class Run {
    String tempTableName;

    public Run() throws DatabaseException {
      this.tempTableName = SortOperator.this.transaction.createTempTable(SortOperator.this.operatorSchema);
    }

    public void addRecord(List<DataBox> values) throws DatabaseException {
      SortOperator.this.transaction.addRecord(this.tempTableName, values);
    }

    public void addRecords(List<Record> records) throws DatabaseException {
      for (Record r: records) {
        this.addRecord(r.getValues());
      }
    }

    public Iterator<Record> iterator() throws DatabaseException {
      return SortOperator.this.transaction.getRecordIterator(this.tempTableName);
    }

    public String tableName() {
      return this.tempTableName;
    }
  }


  /**
   * Returns a NEW run that is the sorted version of the input run.
   * Can do an in memory sort over all the records in this run
   * using one of Java's built-in sorting methods.
   * Note: Don't worry about modifying the original run.
   * Returning a new run would bring one extra page in memory beyond the
   * size of the buffer, but it is done this way for ease.
   */
  public Run sortRun(Run run) throws DatabaseException {
    Run sorted = new Run();
    List<Record> records = new ArrayList<>();
    Iterator<Record> iter = run.iterator();
    while (iter.hasNext()) {
      records.add(iter.next());
    }
    Collections.sort(records, comparator);
    sorted.addRecords(records);
    return sorted;
  }



  /**
   * Given a list of sorted runs, returns a new run that is the result
   * of merging the input runs. You should use a Priority Queue (java.util.PriorityQueue)
   * to determine which record should be should be added to the output run next.
   * It is recommended that your Priority Queue hold Pair<Record, Integer> objects
   * where a Pair (r, i) is the Record r with the smallest value you are
   * sorting on currently unmerged from run i.
   */
  public Run mergeSortedRuns(List<Run> runs) throws DatabaseException {
    Run done = new Run();
    PriorityQueue<Pair<Record, Integer>> pq = new PriorityQueue<>(runs.size(), new RecordPairComparator());
    List<Iterator<Record>> iters = new ArrayList<>();
    for (int i = 0; i < runs.size(); i++) {
      Iterator<Record> currIter = runs.get(i).iterator();
      if (currIter.hasNext()) {
        pq.add(new Pair<>(currIter.next(), i));
      }
      iters.add(currIter);
    }
    while (!pq.isEmpty()) {
      Pair<Record, Integer> next = pq.poll();
      done.addRecord(next.getFirst().getValues());
      if (iters.get(next.getSecond()).hasNext()) {
        pq.add(new Pair<>(iters.get(next.getSecond()).next(), next.getSecond()));
      }
    }
    return done;
  }

  /**
   * Given a list of N sorted runs, returns a list of
   * sorted runs that is the result of merging (numBuffers - 1)
   * of the input runs at a time.
   */
  public List<Run> mergePass(List<Run> runs) throws DatabaseException {
    List<Run> res = new ArrayList<>();
    while (runs.size() > numBuffers - 1) {
      res.add(mergeSortedRuns(runs.subList(0, numBuffers - 1)));
      runs = runs.subList(numBuffers - 1, runs.size());
    }
    res.add(mergeSortedRuns(runs));
    return res;
  }


  /**
   * Does an external merge sort on the table with name tableName
   * using numBuffers.
   * Returns the name of the table that backs the final run.
   */
  public String sort() throws DatabaseException {
    // get table
    RecordIterator iter = this.transaction.getRecordIterator(this.tableName);

    // pass 1: in place
    List<Run> runs = new ArrayList<>();
    Run curr = new Run();
    int count = 0;
    while (iter.hasNext()) {
      if (count == this.numBuffers) {
        runs.add(sortRun(curr));
        curr = new Run();
        count = 0;
      }
      curr.addRecord(iter.next().getValues());
      count++;
    }
    runs.add(sortRun(curr));

    // pass 2: merge
    while (runs.size() > 1) {
      runs = mergePass(runs);
    }

    return runs.get(0).tableName();
  }

  private class RecordPairComparator implements Comparator<Pair<Record, Integer>> {
    public int compare(Pair<Record, Integer> o1, Pair<Record, Integer> o2) {
      return SortOperator.this.comparator.compare(o1.getFirst(), o2.getFirst());

    }
  }
  public Run createRun() throws DatabaseException {
    return new Run();
  }
}

