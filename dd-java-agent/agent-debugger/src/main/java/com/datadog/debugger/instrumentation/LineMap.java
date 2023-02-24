package com.datadog.debugger.instrumentation;

import java.util.Map;
import java.util.TreeMap;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;

/**
 * Custom [line number -> {@linkplain LabelNode}] map for easier determination of local variables
 * scopes
 */
class LineMap {
  private final TreeMap<Integer, LabelNode> lineLabels = new TreeMap<>();

  void addLine(LineNumberNode lineNode) {
    lineLabels.put(lineNode.line, lineNode.start);
  }

  LabelNode getLineLabel(int line) {
    Map.Entry<Integer, LabelNode> nextLine = lineLabels.ceilingEntry(line);
    if (nextLine == null) return null;
    return nextLine.getValue();
  }

  int getLine(Label label) {
    for (Map.Entry<Integer, LabelNode> entry : lineLabels.entrySet()) {
      if (entry.getValue().getLabel().equals(label)) {
        return entry.getKey();
      }
    }
    return -1;
  }

  boolean isEmpty() {
    return lineLabels.isEmpty();
  }
}
