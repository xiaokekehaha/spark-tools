package lakala.models

import org.apache.spark.mllib.evaluation.BinaryClassificationMetrics
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.tree.GradientBoostedTrees
import org.apache.spark.mllib.tree.configuration.BoostingStrategy
import org.apache.spark.mllib.tree.model.GradientBoostedTreesModel
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Administrator on 2017/3/30.
  */
object GradientBoostingRegressionForLK {
  val sparkConf = new SparkConf().setAppName("GradientBoostingRegressionForLK")
  val sc = new SparkContext(sparkConf)
  // sc is an existing SparkContext.
  val hc = new HiveContext(sc)

  def main(args: Array[String]): Unit = {


    if(args.length!=5){
      println("请输入参数：trainingData对应的库名、表名、模型运行时间")
      System.exit(0)
    }

    //分别传入库名、表名、对比效果路径
    val database = args(0)
    val table = args(1)
    val date = args(2)
    //gbdt参数
    val treeNums = args(3).toInt
    val depth = args(4).toInt

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
    val splits = data.randomSplit(Array(0.6, 0.4))
    val (trainingData, testData) = (splits(0), splits(1))
    // Train a GradientBoostedTrees model.
    // The defaultParams for Regression use SquaredError by default.
    val boostingStrategy = BoostingStrategy.defaultParams("Regression")
    boostingStrategy.setNumIterations(30) // Note: Use more iterations in practice.
    boostingStrategy.treeStrategy.setMaxDepth(6)
    boostingStrategy.treeStrategy.setMinInstancesPerNode(50)

    // Empty categoricalFeaturesInfo indicates all features are continuous.
    //boostingStrategy.treeStrategy.setCategoricalFeaturesInfo( scala.collection.mutable.Map[Int, Int]())
    //boostingStrategy.treeStrategy.categoricalFeaturesInfo = Map[Int, Int]()
    val model = GradientBoostedTrees.train(trainingData, boostingStrategy)

    // Evaluate model on test instances and compute test error
    val predictionAndLabels = testData.map { point =>
      val prediction = model.predict(point.features)
      (point.label, prediction)
    }

    predictionAndLabels.map(x => {"predicts: "+x._1+"--> labels:"+x._2}).saveAsTextFile(s"hdfs://ns1/tmp/$date/predictionAndLabels")
    //===================================================================
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

    val testMSE = predictionAndLabels.map{ case(v, p) => math.pow((v - p), 2)}.mean()
    sc.makeRDD(Seq("Test Mean Squared Error = " + testMSE)).saveAsTextFile(s"hdfs://ns1/tmp/$date/testMSE")
    sc.makeRDD(Seq("GradientBoostingRegression model: " + model.toDebugString)).saveAsTextFile(s"hdfs://ns1/tmp/$date/GBDT")
  }

  def saveModel(): Unit ={
    val data = hc.sql(s"select * from lkl_card_score.phone_variable_tnh_creditcardrepayments_train_tc_20170714_result").map{
      row =>
        val arr = new ArrayBuffer[Double]()
        //剔除处理label、contact字段
        for(i <- 2 until row.size){
          if(row.isNullAt(i)){
            arr += 0.0
          }else if(row.get(i).isInstanceOf[Double])
            arr += row.getDouble(i)
          else if(row.get(i).isInstanceOf[Long])
            arr += row.getLong(i).toDouble
        }
        //label、contact数据单独处理
        (row.getDouble(0),row.getString(1),LabeledPoint(row.getDouble(0), Vectors.dense(arr.toArray)))
    }
    val trainingData = data.map(row => row._3)
    val boostingStrategy = BoostingStrategy.defaultParams("Regression")
    boostingStrategy.setNumIterations(30) // Note: Use more iterations in practice.
    boostingStrategy.treeStrategy.setMaxDepth(6)
    boostingStrategy.treeStrategy.setMinInstancesPerNode(50)
    // Empty categoricalFeaturesInfo indicates all features are continuous.
    //boostingStrategy.treeStrategy.setCategoricalFeaturesInfo( scala.collection.mutable.Map[Int, Int]())
    //boostingStrategy.treeStrategy.categoricalFeaturesInfo = Map[Int, Int]()
    val model = GradientBoostedTrees.train(trainingData, boostingStrategy)
    model.save(sc,"hdfs://ns1/user/luhuamin/20170718/tnh/model")

    //全量打分
    val modelGBDT = GradientBoostedTreesModel.load(sc,"hdfs://ns1/user/luhuamin/20170718/tnh/model")
    //打分
    val preditDataGBDT = data.map { point =>
      val prediction = model.predict(point._3.features)
      (point._1,point._2, prediction)
    }
    //preditData保存
    preditDataGBDT.saveAsTextFile(s"hdfs://ns1/user/luhuamin/20170718/tnh/predictionAndLabels")
  }

  //全量打分
  def predictScore(): Unit ={
    val data = hc.sql(s"select * from lkl_card_score.phone_variable_tnh_creditcardrepayments_20170724_result").map{
      row =>
        val arr = new ArrayBuffer[Double]()
        //剔除处理label、contact字段
        for(i <- 1 until row.size){
          if(row.isNullAt(i)){
            arr += 0.0
          }else if(row.get(i).isInstanceOf[Double])
            arr += row.getDouble(i)
          else if(row.get(i).isInstanceOf[Long])
            arr += row.getLong(i).toDouble
          else arr += 0.0
        }
        //label、contact数据单独处理
        (row.getString(0),Vectors.dense(arr.toArray))
    }
    //全量打分
    val modelGBDT = GradientBoostedTreesModel.load(sc,"hdfs://ns1/user/luhuamin/20170718/tnh/model")
    //打分
    val preditDataGBDT = data.map { point =>
      val prediction = modelGBDT.predict(point._2)
      (point._1, prediction)
    }
    //preditData保存
    preditDataGBDT.saveAsTextFile(s"hdfs://ns1/user/luhuamin/20170724/tnh/predictionAndLabels")
  }
}
