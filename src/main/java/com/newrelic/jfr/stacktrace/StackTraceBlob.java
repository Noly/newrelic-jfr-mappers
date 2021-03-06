package com.newrelic.jfr.stacktrace;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import jdk.jfr.consumer.RecordedStackTrace;

public class StackTraceBlob {
  private static final int JSON_SCHEMA_VERSION = 1;
  private static final int HEADROOM_75PC = 3 * 1024;

  public static String encode(RecordedStackTrace trace) {
    var payload = new ArrayList<Map<String, String>>();
    var frames = trace.getFrames();
    for (int i = 0; i < frames.size(); i++) {
      var frameData = new HashMap<String, String>();
      var frame = frames.get(i);
      var method = frame.getMethod();
      var methodDesc = method.getType().getName() + "." + method.getName() + method.getDescriptor();
      frameData.put("desc", methodDesc);
      frameData.put("line", "" + frame.getLineNumber());
      frameData.put("bytecodeIndex", "" + frame.getBytecodeIndex());
      payload.add(frameData);
    }

    try {
      return new String(jsonWrite(payload, Optional.empty()).getBytes());
    } catch (IOException e) {
      throw new RuntimeException("Failed to generate stacktrace json", e);
    }
  }

  static String jsonWrite(List<Map<String, String>> frames, Optional<Integer> limit)
      throws IOException {
    var strOut = new StringWriter();
    var jsonWriter = new JsonWriter(strOut);
    var isTruncated = limit.isPresent();
    var frameCount = frames.size();
    if (isTruncated && limit.get() < frameCount) {
      frameCount = limit.get();
    }
    jsonWriter.beginObject();
    jsonWriter.name("type").value("stacktrace");
    jsonWriter.name("language").value("java");
    jsonWriter.name("version").value(JSON_SCHEMA_VERSION);
    jsonWriter.name("truncated").value(isTruncated);
    jsonWriter.name("payload").beginArray();
    for (int i = 0; i < frameCount; i++) {
      var frame = frames.get(i);
      jsonWriter.beginObject();
      jsonWriter.name("desc").value(frame.get("desc"));
      jsonWriter.name("line").value(frame.get("line"));
      jsonWriter.name("bytecodeIndex").value(frame.get("bytecodeIndex"));
      jsonWriter.endObject();
    }

    jsonWriter.endArray();
    jsonWriter.endObject();
    var out = strOut.toString();
    var length = out.length();
    if (length > HEADROOM_75PC) {
      // Truncate the stack frame and try again
      int numFrames = frames.size() * HEADROOM_75PC / length;
      return jsonWrite(frames, Optional.of(numFrames));
    } else {
      return out;
    }
  }
}
