//  Copyright (c) 2013, Facebook, Inc.  All rights reserved.

/**
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
package com.facebook.hive.orc.statistics;

import com.facebook.hive.orc.OrcProto;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;

public class ColumnStatisticsImpl implements ColumnStatistics {

  private static final class BooleanStatisticsImpl extends ColumnStatisticsImpl
      implements BooleanColumnStatistics {
    private long trueCount = 0;

    BooleanStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.BucketStatistics bkt = stats.getBucketStatistics();
      trueCount = bkt.getCount(0);
    }

    BooleanStatisticsImpl() {
    }

    @Override
    public void reset() {
      super.reset();
      trueCount = 0;
    }

    @Override
    public void updateBoolean(boolean value) {
      if (value) {
        trueCount += 1;
      }
    }

    @Override
    public void merge(ColumnStatisticsImpl other) {
      super.merge(other);
      BooleanStatisticsImpl bkt = (BooleanStatisticsImpl) other;
      trueCount += bkt.trueCount;
    }

    @Override
    public OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder builder = super.serialize();
      OrcProto.BucketStatistics.Builder bucket =
        OrcProto.BucketStatistics.newBuilder();
      bucket.addCount(trueCount);
      builder.setBucketStatistics(bucket);
      return builder;
    }

    @Override
    public long getFalseCount() {
      return getNumberOfValues() - trueCount;
    }

    @Override
    public long getTrueCount() {
      return trueCount;
    }

    @Override
    public String toString() {
      return super.toString() + " true: " + trueCount;
    }
  }

  private static final class IntegerStatisticsImpl extends ColumnStatisticsImpl
      implements IntegerColumnStatistics {

    private long minimum = Long.MAX_VALUE;
    private long maximum = Long.MIN_VALUE;
    private long sum = 0;
    private boolean hasMinimum = false;
    private boolean overflow = false;

    IntegerStatisticsImpl() {
    }

    IntegerStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.IntegerStatistics intStat = stats.getIntStatistics();
      if (intStat.hasMinimum()) {
        hasMinimum = true;
        minimum = intStat.getMinimum();
      }
      if (intStat.hasMaximum()) {
        maximum = intStat.getMaximum();
      }
      if (intStat.hasSum()) {
        sum = intStat.getSum();
      } else {
        overflow = true;
      }
    }

    @Override
    public void reset() {
      super.reset();
      hasMinimum = false;
      minimum = Long.MAX_VALUE;
      maximum = Long.MIN_VALUE;
      sum = 0;
      overflow = false;
    }

    @Override
    public void updateInteger(long value) {
      if (!hasMinimum) {
        hasMinimum = true;
        minimum = value;
        maximum = value;
      } else if (value < minimum) {
        minimum = value;
      } else if (value > maximum) {
        maximum = value;
      }
      if (!overflow) {
        boolean wasPositive = sum >= 0;
        sum += value;
        if ((value >= 0) == wasPositive) {
          overflow = (sum >= 0) != wasPositive;
        }
      }
    }

    @Override
    public void merge(ColumnStatisticsImpl other) {
      IntegerStatisticsImpl otherInt = (IntegerStatisticsImpl) other;
      if (!hasMinimum) {
        hasMinimum = otherInt.hasMinimum;
        minimum = otherInt.minimum;
        maximum = otherInt.maximum;
      } else if (otherInt.hasMinimum) {
        if (otherInt.minimum < minimum) {
          minimum = otherInt.minimum;
        }
        if (otherInt.maximum > maximum) {
          maximum = otherInt.maximum;
        }
      }
      super.merge(other);
      overflow |= otherInt.overflow;
      if (!overflow) {
        boolean wasPositive = sum >= 0;
        sum += otherInt.sum;
        if ((otherInt.sum >= 0) == wasPositive) {
          overflow = (sum >= 0) != wasPositive;
        }
      }
    }

    @Override
    public OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder builder = super.serialize();
      OrcProto.IntegerStatistics.Builder intb =
        OrcProto.IntegerStatistics.newBuilder();
      if (hasMinimum) {
        intb.setMinimum(minimum);
        intb.setMaximum(maximum);
      }
      if (!overflow) {
        intb.setSum(sum);
      }
      builder.setIntStatistics(intb);
      return builder;
    }

    @Override
    public long getMinimum() {
      return minimum;
    }

    @Override
    public long getMaximum() {
      return maximum;
    }

    @Override
    public boolean isSumDefined() {
      return !overflow;
    }

    @Override
    public long getSum() {
      return sum;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(super.toString());
      if (hasMinimum) {
        buf.append(" min: ");
        buf.append(minimum);
        buf.append(" max: ");
        buf.append(maximum);
      }
      if (!overflow) {
        buf.append(" sum: ");
        buf.append(sum);
      }
      return buf.toString();
    }
  }

  private static final class DoubleStatisticsImpl extends ColumnStatisticsImpl
       implements DoubleColumnStatistics {
    private boolean hasMinimum = false;
    private double minimum = Double.MAX_VALUE;
    private double maximum = Double.MIN_VALUE;
    private double sum = 0;

    DoubleStatisticsImpl() {
    }

    DoubleStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.DoubleStatistics dbl = stats.getDoubleStatistics();
      if (dbl.hasMinimum()) {
        hasMinimum = true;
        minimum = dbl.getMinimum();
      }
      if (dbl.hasMaximum()) {
        maximum = dbl.getMaximum();
      }
      if (dbl.hasSum()) {
        sum = dbl.getSum();
      }
    }

    @Override
    public void reset() {
      super.reset();
      hasMinimum = false;
      minimum = Double.MAX_VALUE;
      maximum = Double.MIN_VALUE;
      sum = 0;
    }

    @Override
    public void updateDouble(double value) {
      if (!hasMinimum) {
        hasMinimum = true;
        minimum = value;
        maximum = value;
      } else if (value < minimum) {
        minimum = value;
      } else if (value > maximum) {
        maximum = value;
      }
      sum += value;
    }

    @Override
    public void merge(ColumnStatisticsImpl other) {
      super.merge(other);
      DoubleStatisticsImpl dbl = (DoubleStatisticsImpl) other;
      if (!hasMinimum) {
        hasMinimum = dbl.hasMinimum;
        minimum = dbl.minimum;
        maximum = dbl.maximum;
      } else if (dbl.hasMinimum) {
        if (dbl.minimum < minimum) {
          minimum = dbl.minimum;
        }
        if (dbl.maximum > maximum) {
          maximum = dbl.maximum;
        }
      }
      sum += dbl.sum;
    }

    @Override
    public OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder builder = super.serialize();
      OrcProto.DoubleStatistics.Builder dbl =
        OrcProto.DoubleStatistics.newBuilder();
      if (hasMinimum) {
        dbl.setMinimum(minimum);
        dbl.setMaximum(maximum);
      }
      dbl.setSum(sum);
      builder.setDoubleStatistics(dbl);
      return builder;
    }

    @Override
    public double getMinimum() {
      return minimum;
    }

    @Override
    public double getMaximum() {
      return maximum;
    }

    @Override
    public double getSum() {
      return sum;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(super.toString());
      if (hasMinimum) {
        buf.append(" min: ");
        buf.append(minimum);
        buf.append(" max: ");
        buf.append(maximum);
      }
      buf.append(" sum: ");
      buf.append(sum);
      return buf.toString();
    }
  }

  private static final class StringStatisticsImpl extends ColumnStatisticsImpl
      implements StringColumnStatistics {
    private String minimum = null;
    private String maximum = null;

    StringStatisticsImpl() {
    }

    StringStatisticsImpl(OrcProto.ColumnStatistics stats) {
      super(stats);
      OrcProto.StringStatistics str = stats.getStringStatistics();
      if (str.hasMaximum()) {
        maximum = str.getMaximum();
      }
      if (str.hasMinimum()) {
        minimum = str.getMinimum();
      }
    }

    @Override
    public void reset() {
      super.reset();
      minimum = null;
      maximum = null;
    }

    @Override
    public void updateString(String value) {
      if (minimum == null) {
        minimum = value;
        maximum = value;
      } else if (minimum.compareTo(value) > 0) {
        minimum = value;
      } else if (maximum.compareTo(value) < 0) {
        maximum = value;
      }
    }

    @Override
    public void merge(ColumnStatisticsImpl other) {
      super.merge(other);
      final StringStatisticsImpl str = (StringStatisticsImpl) other;
      if (minimum == null) {
        minimum = str.minimum;
        maximum = str.maximum;
      } else if (str.minimum != null) {
        if (minimum.compareTo(str.minimum) > 0) {
          minimum = str.minimum;
        }
        if (maximum.compareTo(str.maximum) < 0) {
          maximum = str.maximum;
        }
      }
    }

    @Override
    public OrcProto.ColumnStatistics.Builder serialize() {
      OrcProto.ColumnStatistics.Builder result = super.serialize();
      OrcProto.StringStatistics.Builder str =
        OrcProto.StringStatistics.newBuilder();
      if (getNumberOfValues() != 0) {
        str.setMinimum(minimum);
        str.setMaximum(maximum);
      }
      result.setStringStatistics(str);
      return result;
    }

    @Override
    public String getMinimum() {
      return minimum;
    }

    @Override
    public String getMaximum() {
      return maximum;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder(super.toString());
      if (getNumberOfValues() != 0) {
        buf.append(" min: ");
        buf.append(minimum);
        buf.append(" max: ");
        buf.append(maximum);
      }
      return buf.toString();
    }
  }

  private long count = 0;

  ColumnStatisticsImpl(OrcProto.ColumnStatistics stats) {
    if (stats.hasNumberOfValues()) {
      count = stats.getNumberOfValues();
    }
  }

  ColumnStatisticsImpl() {
  }

  public void increment() {
    count += 1;
  }

  public void updateBoolean(boolean value) {
    throw new UnsupportedOperationException("Can't update boolean");
  }

  public void updateInteger(long value) {
    throw new UnsupportedOperationException("Can't update integer");
  }

  public void updateDouble(double value) {
    throw new UnsupportedOperationException("Can't update double");
  }

  public void updateString(String value) {
    throw new UnsupportedOperationException("Can't update string");
  }

  public void merge(ColumnStatisticsImpl stats) {
    count += stats.count;
  }

  public void reset() {
    count = 0;
  }

  @Override
  public long getNumberOfValues() {
    return count;
  }

  @Override
  public String toString() {
    return "count: " + count;
  }

  public OrcProto.ColumnStatistics.Builder serialize() {
    OrcProto.ColumnStatistics.Builder builder =
      OrcProto.ColumnStatistics.newBuilder();
    builder.setNumberOfValues(count);
    return builder;
  }

  public static ColumnStatisticsImpl create(ObjectInspector inspector) {
    switch (inspector.getCategory()) {
      case PRIMITIVE:
        switch (((PrimitiveObjectInspector) inspector).getPrimitiveCategory()) {
          case BOOLEAN:
            return new BooleanStatisticsImpl();
          case BYTE:
          case SHORT:
          case INT:
          case LONG:
            return new IntegerStatisticsImpl();
          case FLOAT:
          case DOUBLE:
            return new DoubleStatisticsImpl();
          case STRING:
            return new StringStatisticsImpl();
          default:
            return new ColumnStatisticsImpl();
        }
      default:
        return new ColumnStatisticsImpl();
    }
  }

  public static ColumnStatisticsImpl deserialize(OrcProto.ColumnStatistics stats) {
    if (stats.hasBucketStatistics()) {
      return new BooleanStatisticsImpl(stats);
    } else if (stats.hasIntStatistics()) {
      return new IntegerStatisticsImpl(stats);
    } else if (stats.hasDoubleStatistics()) {
      return new DoubleStatisticsImpl(stats);
    } else if (stats.hasStringStatistics()) {
      return new StringStatisticsImpl(stats);
    } else {
      return new ColumnStatisticsImpl(stats);
    }
  }
}
