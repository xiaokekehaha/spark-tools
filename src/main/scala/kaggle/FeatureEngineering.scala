package kaggle

import org.apache.spark
import org.apache.spark.ml.feature._
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by Administrator on 2017/6/7.
  */
object FeatureEngineering {

  val sparkConf = new SparkConf().setAppName("kaggle")
  val sc = new SparkContext(sparkConf)
  val sqlContext = new SQLContext(sc)

  def main(args: Array[String]): Unit = {

  }

  //pca降维处理
  def pca(): Unit ={
      sparkConf.setAppName("kaggle.PCA")
      val data = Array(
        //稀疏向量
        Vectors.sparse(5, Seq((1, 1.0), (3, 7.0))),
        Vectors.dense(2.0, 0.0, 3.0, 4.0, 5.0),
        Vectors.dense(4.0, 0.0, 0.0, 6.0, 7.0)
      )
      //val df = (data.map(Tuple1.apply)).toDF("features")
      val df = sqlContext.createDataFrame(data.map(Tuple1.apply)).toDF("features")
      //pca降维处理
      val pca = new PCA()
        .setInputCol("features")
        .setOutputCol("pcaFeatures")
        .setK(3)
        .fit(df)
      val pcaDF = pca.transform(df)
      val result = pcaDF.select("pcaFeatures")
      result.show()
  }

  //分词处理 只针对英文 ， 中文分词使用hanlp
  def tokenizer(): Unit ={
    //构造sentence
    val sentenceDataFrame = sqlContext.createDataFrame(Seq(
      (0, "Hi I heard about Spark"),
      (1, "I wish Java could use case classes"),
      (2, "Logistic,regression,models,are,neat")
    )).toDF("label", "sentence")

    //
    val tokenizer = new Tokenizer().setInputCol("sentence").setOutputCol("words")
    //val regexTokenizer = new RegexTokenizer().setInputCol("sentence").setOutputCol("words").setPattern("\\W")
    val regexTokenizer = new RegexTokenizer()
      .setInputCol("sentence")
      .setOutputCol("words")
      .setPattern("\\W") // alternatively .setPattern("\\w+").setGaps(false)

    val tokenized = tokenizer.transform(sentenceDataFrame)
    tokenized.select("words", "label").take(3).foreach(println)
    val regexTokenized = regexTokenizer.transform(sentenceDataFrame)
    regexTokenized.select("words", "label").take(3).foreach(println)
  }

  //根据阀值二值化处理
  def binarizer(): Unit ={
    val data = Array((0, 0.1), (1, 0.8), (2, 0.2))
    val dataFrame = sqlContext.createDataFrame(data).toDF("label", "feature")
    //设定阀值
    val binarize  = new Binarizer()
      .setInputCol("feature")
      .setOutputCol("binarized_feature")
      .setThreshold(0.5)

    val binarizedDataFrame = binarize.transform(dataFrame)
    val binarizedFeatures = binarizedDataFrame.select("binarized_feature")
    binarizedFeatures.collect().foreach(println)
  }

  //多项式扩展
  def polynomialExpansion(): Unit ={
    val data = Array(
      Vectors.dense(-2.0, 2.3),
      Vectors.dense(0.0, 0.0),
      Vectors.dense(0.6, -1.1)
    )
    val df = sqlContext.createDataFrame(data.map(Tuple1.apply)).toDF("features")
    val polynomialExpansion = new PolynomialExpansion()
      .setInputCol("features")
      .setOutputCol("polyFeatures")
      .setDegree(3)
    val polyDF = polynomialExpansion.transform(df)
    polyDF.select("polyFeatures").take(3).foreach(println)
  }

  //oneHotEncoder处理，哑变量处理
  def oneHotEncoder(df:DataFrame): Unit ={
    //创建dataFrame
    //val df = sqlContext.createDataFrame(Seq((0, "a"), (1, "b"), (2, "c"), (3, "a"), (4, "a"), (5, "c"))).toDF("id", "category")
    //索引化dataframe，即统计获取特征的属性类别（a、b、c、d）
    val indexer = new StringIndexer().setInputCol("category").setOutputCol("categoryIndex").fit(df)
    //indexer.labels
    //获取属性类别对应的统计值
    val indexed = indexer.transform(df)
    //indexed.show()
    //获取属性类别对应的稀疏矩阵
    val encoder = new OneHotEncoder().setInputCol("categoryIndex").setOutputCol("categoryVec")
    val encoded = encoder.transform(indexed)
    //encoded.show()
    val data = encoded.rdd.map { x =>
      {
        //稀疏矩阵转换成稠密矩阵
        val featureVector = Vectors.dense(x.getAs[org.apache.spark.mllib.linalg.SparseVector]("categoryVec").toArray)
        //val label = x.getAs[java.lang.Integer]("id").toDouble
        //LabeledPoint(label, featureVector)
        featureVector
      }
    }
    //data.collect()
    //var result = sqlContext.createDataFrame(data)
  }

  //StringIndexer 标签索引处理
  def stringIndexer(): Unit ={
    val df = sqlContext.createDataFrame(
      Seq((0, "a"), (1, "b"), (2, "c"), (3, "a"), (4, "a"), (5, "c")
      ) )
      .toDF("id", "category")
    val indexer = new StringIndexer()
      .setInputCol("category")
      .setOutputCol("categoryIndex")
    val indexed = indexer.fit(df).transform(df)
    indexed.show()
  }

  //归一化处理
  def minMaxScaler(): Unit ={
      val assembler = new VectorAssembler()
      assembler.setInputCols(Array("Pclass","Sex","Age","SibSp","Parch","Fare","Embarked"))
        .setOutputCol("features")
    val df = sqlContext.createDataFrame(
      Seq((0, "a"), (1, "b"), (2, "c"), (3, "a"), (4, "a"), (5, "c")
      ) )
      .toDF("id", "category")
      val tmp5 = assembler.transform(df)
      val scaler = new MinMaxScaler()
      scaler.setInputCol("features").setOutputCol("scaledFeatures").setMin(0).setMax(1)
      val tmp6 = scaler.fit(tmp5).transform(tmp5)
  }

  //IndexToString
  //VectorIndexer
}
