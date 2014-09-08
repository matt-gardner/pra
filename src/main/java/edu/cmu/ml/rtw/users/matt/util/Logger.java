package edu.cmu.ml.rtw.users.matt.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

/**
 * I probably should be using log4j, or something, but this lets me easily output both to the
 * terminal and to a file.
 *
 * I also copied the timer stuff from Jayant's log classes.
 */
public class Logger {
  // Each thread has its own collection of timers.
  private static final Map<Long, Map<String, Long>> activeTimers =
      Collections.synchronizedMap(Maps.<Long, Map<String, Long>>newHashMap());
  private static final Map<String, Long> timerSumTimes =
      Collections.synchronizedMap(Maps.<String, Long>newHashMap());
  private static final Map<String, Long> timerInvocations =
      Collections.synchronizedMap(Maps.<String, Long>newHashMap());
  private static final long TIME_DENOMINATOR = 1000000;

  private static String logfile;
  private static boolean warned = false;

  public Logger() {
  }

  public static void setLogFile(String lf) {
    logfile = lf;
    new File(logfile).getParentFile().mkdirs();
  }

  public static void log(int to_log) {
    log("" + to_log, false);
  }

  public static void log(double to_log) {
    log("" + to_log, false);
  }

  public static void log(long to_log) {
    log("" + to_log, false);
  }

  public static void log(String to_log) {
    log(to_log, false);
  }

  public static void log(String to_log, boolean no_new_line) {
    if (!no_new_line) {
      System.out.println(to_log);
    } else {
      System.out.print(to_log);
    }
    if (logfile == null) {
      if (!warned) {
        System.out.println("NO LOGFILE SET, JUST PRINTING TO STDOUT");
        warned = true;
      }
      return;
    }
    try {
      FileWriter writer = new FileWriter(new File(logfile), true);
      writer.write(to_log);
      if (!no_new_line) {
        writer.write("\n");
      }
      writer.close();
    } catch (IOException e) {
      throw new RuntimeException("Failed to write to log file!");
    }
  }

  public static void logTimer(String timer_name) {
    if (!timerSumTimes.containsKey(timer_name)) {
      log("No timer named " + timer_name);
      return;
    }
    long millis = getTimerElapsedTime(timer_name);
    long invocations = getTimerInvocations(timer_name);
    double per_invocation = millis / (double) invocations;
    log("TIMER " + timer_name + ": " + (millis / 1000.0) + " seconds; " +
        invocations + " invocations (" + per_invocation + " millis per invocation)");
  }

  public static void logAllTimers() {
    for (String timer_name : getAllTimers()) {
      logTimer(timer_name);
    }
  }

  public static void startTimer(String timer_name) {
    Long thread_id = Thread.currentThread().getId();
    if (!activeTimers.containsKey(thread_id)) {
      activeTimers.put(thread_id, Maps.<String, Long>newHashMap());
    }
    activeTimers.get(thread_id).put(timer_name, System.nanoTime());
  }

  public static long stopTimer(String timer_name) {
    Long thread_id = Thread.currentThread().getId();
    Preconditions.checkArgument(activeTimers.get(thread_id).containsKey(timer_name));
    long end = System.nanoTime();
    long start = activeTimers.get(thread_id).remove(timer_name);

    if (!timerSumTimes.containsKey(timer_name)) {
      timerSumTimes.put(timer_name, 0L);
    }
    if (!timerInvocations.containsKey(timer_name)) {
      timerInvocations.put(timer_name, 0L);
    }
    timerSumTimes.put(timer_name, timerSumTimes.get(timer_name) + (end - start));
    timerInvocations.put(timer_name, timerInvocations.get(timer_name) + 1L);

    return (end - start) / TIME_DENOMINATOR;
  }

  private static Set<String> getAllTimers() {
    return timerSumTimes.keySet();
  }

  private static long getTimerElapsedTime(String timer_name) {
    return timerSumTimes.get(timer_name) / TIME_DENOMINATOR; // Return time in milliseconds.
  }

  private static long getTimerInvocations(String timer_name) {
    return timerInvocations.get(timer_name);
  }
}
