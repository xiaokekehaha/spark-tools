package lakala.models

import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.RandomForest
import org.apache.spark.sql.hive.HiveContext

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Administrator on 2017/3/29.
  */
object RandomForestClassificationForLK {
  def main(args: Array[String]): Unit = {
    val sparkConf = new SparkConf().setAppName("RandomForestClassificationForLK")
    val sc = new SparkContext(sparkConf)
    // sc is an existing SparkContext.
    val hc = new HiveContext(sc)

    if(args.length!=3){
      println("请输入参数：trainingData对应的库名、表名、模型运行时间")
      System.exit(0)
    }

    //分别传入库名、表名、对比效果路径
    val database = args(0)
    val table = args(1)
    val date = args(2)

    //提取数据集 RDD[LabeledPoint]
    val data = hc.sql(s"select * from $database.$table").map{
      row =>
        val arr = new ArrayBuffer[Double]()
        //剔除label、contact字段
        for(i <- 2 until row.size){
          if(row.isNullAt(i)){
            arr += 0.0
          }else if(row.get(i).isInstanceOf[Double])
            arr += row.getDouble(i)
          else if(row.get(i).isInstanceOf[Long])
            arr += row.getLong(i).toDouble
        }
        LabeledPoint(row.getDouble(0), Vectors.dense(arr.toArray))
    }

    // Split the data into training and test sets (30% held out for testing)
    val splits = data.randomSplit(Array(0.7, 0.3))
    val (trainingData, testData) = (splits(0), splits(1))

    val numClasses = 2
    val categoricalFeaturesInfo = Map[Int, Int]()
    val numTrees = 3 // Use more in practice.
    val featureSubsetStrategy = "auto" // Let the algorithm choose.
    val impurity = "gini"
    val maxDepth = 4
    val maxBins = 32

    val model = RandomForest.trainClassifier(trainingData, numClasses, categoricalFeaturesInfo,
      numTrees, featureSubsetStrategy, impurity, maxDepth, maxBins)

    // Evaluate model on test instances and compute test error
    val predictionAndLabels = testData.map { point =>
      val prediction = model.predict(point.features)
      (point.label, prediction)
    }

    predictionAndLabels.map(x => {"predicts: "+x._1+"--> labels:"+x._2}).saveAsTextFile(s"hdfs://ns1/tmp/$date/predictionAndLabels")
    //=====================================================================
    //使用BinaryClassificationMetrics评估模型
    val metrics = new BinaryClassificationMetrics(predictionAndLabels)

    // Precision by threshold
    val precision = metrics.precisionByThreshold
    precision.map({case (t, p) =>
      "Threshold: "+t+"Precision:"+p
    }).saveAsTextFile(s"hdfs://ns1/tmp/$date/precision")

    // Recall by threshold
    val recall = metrics.recallByThreshold
    recall.map({case (t, r) =>
      "Threshold: "+t+"Recall:"+r
    }).saveAsTextFile(s"hdfs://ns1/tmp/$date/recall")

    //the beta factor in F-Measure computation.
    val f1Score = metrics.fMeasureByThreshold
    f1Score.map(x => {"Threshold: "+x._1+"--> F-score:"+x._2+"--> Beta = 1"})
      .saveAsTextFile(s"hdfs://ns1/tmp/$date/f1Score")

    /**
      * 如果要选择Threshold, 这三个指标中, 自然F1最为合适
      * 求出最大的F1, 对应的threshold就是最佳的threshold
      */
    /*val maxFMeasure = f1Score.select(max("F-Measure")).head().getDouble(0)
    val bestThreshold = f1Score.where($"F-Measure" === maxFMeasure)
      .select("threshold").head().getDouble(0)*/

    // Precision-Recall Curve
    val prc = metrics.pr
    prc.map(x => {"Recall: " + x._1 + "--> Precision: "+x._2 }).saveAsTextFile(s"hdfs://ns1/tmp/$date/prc")

    // AUPRC，精度，召回曲线下的面积
    val auPRC = metrics.areaUnderPR
    sc.makeRDD(Seq("Area under precision-recall curve = " +auPRC)).saveAsTextFile(s"hdfs://ns1/tmp/$date/auPRC")

    //roc
    val roc = metrics.roc
    roc.map(x => {"FalsePositiveRate:" + x._1 + "--> Recall: " +x._2}).saveAsTextFile(s"hdfs://ns1/tmp/$date/roc")

    // AUC
    val auROC = metrics.areaUnderROC
    sc.makeRDD(Seq("Area under ROC = " + +auROC)).saveAsTextFile(s"hdfs://ns1/tmp/$date/auROC")
    println("Area under ROC = " + auROC)

    val testErr = predictionAndLabels.filter(r => r._1 != r._2).count.toDouble / testData.count()
    sc.makeRDD(Seq("Test Error = " + testErr)).saveAsTextFile(s"hdfs://ns1/tmp/$date/testErr")
    sc.makeRDD(Seq("Learned classification forest model:" + model.toDebugString)).saveAsTextFile(s"hdfs://ns1/tmp/$date/forest")
  }
}
