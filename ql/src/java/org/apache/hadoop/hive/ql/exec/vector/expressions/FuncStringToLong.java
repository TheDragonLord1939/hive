/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.vector.expressions;

import org.apache.hadoop.hive.ql.exec.vector.BytesColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.LongColumnVector;
import org.apache.hadoop.hive.ql.exec.vector.VectorExpressionDescriptor;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;

/**
 * Superclass to support vectorized functions that take a long
 * and return a string, optionally with additional configuration arguments.
 * Used for cast(string), length(string), etc
 */
public abstract class FuncStringToLong extends VectorExpression {
  private static final long serialVersionUID = 1L;

  private int inputCol;
  private int outputCol;

  public FuncStringToLong(int inputCol, int outputCol) {
    super(outputCol);
    this.inputCol = inputCol;
    this.outputCol = outputCol;
  }

  public FuncStringToLong() {
  }

  @Override
  public void evaluate(VectorizedRowBatch batch) {

    if (childExpressions != null) {
      super.evaluateChildren(batch);
    }

    BytesColumnVector inV = (BytesColumnVector) batch.cols[inputCol];
    int[] sel = batch.selected;
    int n = batch.size;
    LongColumnVector outV = (LongColumnVector) batch.cols[outputCol];

    if (n == 0) {
      //Nothing to do
      return;
    }

    if (inV.noNulls) {
      outV.noNulls = true;
      if (inV.isRepeating) {
        outV.isRepeating = true;
        func(outV, inV, 0);
      } else if (batch.selectedInUse) {
        for (int j = 0; j != n; j++) {
          int i = sel[j];
          func(outV, inV, i);
        }
        outV.isRepeating = false;
      } else {
        for (int i = 0; i != n; i++) {
          func(outV, inV, i);
        }
        outV.isRepeating = false;
      }
    } else {
      // Handle case with nulls. Don't do function if the value is null, to save time,
      // because calling the function can be expensive.
      outV.noNulls = false;
      if (inV.isRepeating) {
        outV.isRepeating = true;
        outV.isNull[0] = inV.isNull[0];
        if (!inV.isNull[0]) {
          func(outV, inV, 0);
        }
      } else if (batch.selectedInUse) {
        for (int j = 0; j != n; j++) {
          int i = sel[j];
          outV.isNull[i] = inV.isNull[i];
          if (!inV.isNull[i]) {
            func(outV, inV, i);
          }
        }
        outV.isRepeating = false;
      } else {
        System.arraycopy(inV.isNull, 0, outV.isNull, 0, n);
        for (int i = 0; i != n; i++) {
          if (!inV.isNull[i]) {
            func(outV, inV, i);
          }
        }
        outV.isRepeating = false;
      }
    }
  }

  /* Evaluate result for position i (using bytes[] to avoid storage allocation costs)
   * and set position i of the output vector to the result.
   */
  protected abstract void func(LongColumnVector outV, BytesColumnVector inV, int i);

  public int getOutputCol() {
    return outputCol;
  }

  public void setOutputCol(int outputCol) {
    this.outputCol = outputCol;
  }

  public int getInputCol() {
    return inputCol;
  }

  public void setInputCol(int inputCol) {
    this.inputCol = inputCol;
  }

  @Override
  public String vectorExpressionParameters() {
    return "col " + inputCol;
  }

  @Override
  public VectorExpressionDescriptor.Descriptor getDescriptor() {
    VectorExpressionDescriptor.Builder b = new VectorExpressionDescriptor.Builder();
    b.setMode(VectorExpressionDescriptor.Mode.PROJECTION).setNumArguments(1)
        .setArgumentTypes(VectorExpressionDescriptor.ArgumentType.STRING_FAMILY)
        .setInputExpressionTypes(VectorExpressionDescriptor.InputExpressionType.COLUMN);
    return b.build();
  }
}
