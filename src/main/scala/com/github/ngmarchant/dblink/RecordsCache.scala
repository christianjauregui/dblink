// Copyright (C) 2018  Australian Bureau of Statistics
//
// Author: Neil Marchant
//
// This file is part of dblink.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.github.ngmarchant.dblink

import com.github.ngmarchant.dblink.accumulators.MapLongAccumulator
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

/** Container to store statistics/metadata for a collection of records and
  * facilitate sampling from the attribute domains.
  *
  * This container is broadcast to each executor.
  *
  * @param indexedAttributes indexes for the attributes
  * @param fileSizes number of records in each file
  */
class RecordsCache(val indexedAttributes: IndexedSeq[IndexedAttribute],
                   val fileSizes: Map[FileId, Long]) extends Serializable {
  /** Set the random number generator.
    * Called at the beginning of each iteration on each executor. */
  def setRand(rand: RandomGenerator): Unit = {
    this.indexedAttributes.foreach(_.index.setRand(rand))
  }

  def distortionPrior: Iterator[BetaShapeParameters] = indexedAttributes.iterator.map(_.distortionPrior)

  /** Number of records across all files */
  val numRecords: Long = fileSizes.values.sum

  /** Number of attributes used for matching */
  def numAttributes: Int = indexedAttributes.length

  /** Transform to value ids
    *
    * @param records an RDD of records in raw form (string attribute values)
    * @return an RDD of records where the string attribute values are replaced
    *         by integer value ids
    */
  def transformRecords(records: RDD[Record[String]]): RDD[Record[ValueId]] =
    RecordsCache._transformRecords(records, indexedAttributes)
}

object RecordsCache extends Logging {

  /** Build RecordsCache
    *
    * @param records an RDD of records in raw form (string attribute values)
    * @param attributeSpecs specifications for each record attribute. Must match
    *                       the order of attributes in `records`.
    * @param expectedMaxClusterSize largest expected record cluster size. Used
    *                               as a hint when precaching distributions
    *                               over the non-constant attribute domain.
    * @return a RecordsCache
    */
  def apply(records: RDD[Record[String]],
            attributeSpecs: IndexedSeq[Attribute],
            expectedMaxClusterSize: Int): RecordsCache = {
    val firstRecord = records.take(1).head
    require(firstRecord.values.length == attributeSpecs.length, "attribute specifications do not match the records")

    implicit val rand: RandomGenerator = new MersenneTwister()

    /** Use accumulators to gather record stats in one pass */
    val accFileSizes = new MapLongAccumulator[FileId]
    implicit val sc: SparkContext = records.sparkContext
    sc.register(accFileSizes, "number of records per file")

    val accValueCounts = attributeSpecs.map{ attribute =>
      val acc = new MapLongAccumulator[String]
      sc.register(acc, s"value counts for attribute ${attribute.name}.")
      acc
    }

    info("Gathering statistics from source data files.")
    /** Get file and value counts in a single foreach action */
    records.foreach { case Record(_, fileId, values) =>
      accFileSizes.add((fileId, 1L))
      var i = 0
      while (i < values.length) {
        accValueCounts(i).add(values(i), 1L)
        i += 1
      }
    }

    val fileSizes = accFileSizes.value
    info(s"Finished gathering statistics from ${fileSizes.values.sum} records across ${fileSizes.size} file(s).")

    /** Build an index for each attribute (generates a mapping from strings -> integers) */
    val indexedAttributes = attributeSpecs.zipWithIndex.map { case (attribute, attrId) =>
      info(s"Indexing attribute '${attribute.name}'.")
      val index = AttributeIndex(accValueCounts(attrId).value.mapValues(_.toDouble),
        attribute.similarityFn, Some(1 to expectedMaxClusterSize))
      IndexedAttribute(attribute.name, attribute.similarityFn, attribute.distortionPrior, index)
    }

    new RecordsCache(indexedAttributes, fileSizes)
  }

  private def _transformRecords(records: RDD[Record[String]],
                                indexedAttributes: IndexedSeq[IndexedAttribute]): RDD[Record[ValueId]] = {
    val firstRecord = records.take(1).head
    require(firstRecord.values.length == indexedAttributes.length, "attribute specifications do not match the records")

    records.mapPartitions { partition =>
      partition.map { record =>
        val mappedValues = (record.values, indexedAttributes).zipped.map { case (stringValue, IndexedAttribute(_, _, _, index)) =>
          index.valueIdxOf(stringValue)
        }
        Record[ValueId](record.id, record.fileId, mappedValues)
      }
    }
  }
}