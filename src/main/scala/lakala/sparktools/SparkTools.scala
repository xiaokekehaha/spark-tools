package lakala.sparktools

import java.util.Properties

import org.apache.spark.sql.{SQLContext, SaveMode}
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by root on 16-6-6.
  */
object SparkTools {

  val sparkConf = new SparkConf().setAppName("spark-tools")
  sparkConf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
  sparkConf.set("spark.rdd.compress","true")
  sparkConf.set("spark.hadoop.mapred.output.compress","true")
  sparkConf.set("spark.hadoop.mapred.output.compression.codec","true")
  sparkConf.set("spark.hadoop.mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec")
  sparkConf.set("spark.hadoop.mapred.output.compression.type", "BLOCK")

  def loadFS2Hive(inputPath:String,tableName:String,format:String = "json"): Unit ={
    sparkConf.setAppName("spark-tools.loadHDFS2Hive")
    val sc = new SparkContext(sparkConf)
    val hc = new HiveContext(sc)
    hc.sql(s"create table if not exists $tableName (id int)")
    format match {
      case "json" => hc.read.json(inputPath).write.mode(SaveMode.Overwrite).saveAsTable(tableName)
      case "parquet" => hc.read.parquet(inputPath).write.mode(SaveMode.Overwrite).saveAsTable(tableName)
      case _ => println("don't support")
    }
  }

  def JDBC2FS(host:String,user:String,password:String,db:String,table:String,outputPath:String,port:String = "3306",
              colName:String = null,lowerBound:Long = 0,upperBound:Long = 0,numPartitions:Int = 0): Unit ={
    val url = s"jdbc:mysql://$host:$port/$db?user=$user&password=$password&useUnicode=true&characterEncoding=utf-8&autoReconnect=true&failOverReadOnly=false"
    sparkConf.setAppName("spark-tools.JDBC2FS")
    val sc = new SparkContext(sparkConf)
    val sqlContext = new SQLContext(sc)
    if(colName == null){
      sqlContext.read.jdbc(url,table,new Properties()).write.mode(SaveMode.Overwrite).json(outputPath)
    }else{
      //这个接口就是用来解决表太大，导致内存溢出的问题。
      sqlContext.read.jdbc(url,table,colName,lowerBound,upperBound,numPartitions,new Properties()).write.mode(SaveMode.Overwrite).json(outputPath)
    }
  }

  def hive2FS(table:String,outputPath:String): Unit ={
    sparkConf.setAppName("spark-tools.hive2FS")
    val sc = new SparkContext(sparkConf)
    val hc = new HiveContext(sc)
    val df = hc.sql(s"select * from $table")
    df.write.mode(SaveMode.Overwrite).json(outputPath)
  }

  def loadFS2JDBC(host:String,user:String,password:String,db:String,table:String,inputPath:String,port:String = "3306"): Unit ={
    val url = s"jdbc:mysql://$host:$port/$db?user=$user&password=$password&useUnicode=true&characterEncoding=utf-8&autoReconnect=true&failOverReadOnly=false"
    sparkConf.setAppName("spark-tools.JDBC2FS")
    loadFS2JDBC(url,table,inputPath)
  }

  def loadFS2JDBC(url:String,table:String,inputPath:String): Unit ={
    sparkConf.setAppName("spark-tools.JDBC2FS")
    val sc = new SparkContext(sparkConf)
    val sqlContext = new SQLContext(sc)
    val input = sqlContext.read.json(inputPath)
    input.write.mode(SaveMode.Overwrite).jdbc(url,table,new Properties())
  }

  //load csvfile to hive
  def loadCsv2Hive(inputPath:String,tableName:String): Unit ={
    sparkConf.setAppName("spark-tools.loadCsv2Hive")
    val sc = new SparkContext(sparkConf)
    val hc = new HiveContext(sc)
    hc.sql("use lkl_card_score")
    //val csvDF = sc.textFile(inputPath).map(x=>x.split(",")).saveAsTextFile()
  }

  //应用第三方jar包
  def loadData2HiveNew(inputPath:String,tableName:String): Unit ={
    val sc = new SparkContext(sparkConf)
    val hc = new HiveContext(sc)
    //val inputPath = "file:///home/hadoop/alllevel1.csv"
    //多路径

    val inputPath1 = "file:///home/hadoop/exportdata/train_titanic_3.csv，file:///home/hadoop/exportdata/train_titanic_5.csv"
    //val inputPath2 = "file:///home/hadoop/exportdata/train_titanic_5.csv"
    val csvDF = hc.read.format("com.databricks.spark.csv").option("header", "false").load(inputPath1)
    //val csvDF = hc.read.format("com.databricks.spark.csv").option("header", "false").load("file:///home/hadoop/Allrelationlevel1.csv")
    csvDF.show(10)
    csvDF.cache()
    csvDF.write.mode(SaveMode.Append).saveAsTable("lkl_card_score.fqz_order1608to1702_case_data1_1")
    hc.read.format("com.databricks.spark.csv").option("header", "false").load("file:///home/hadoop/alllevel2.csv").write.mode(SaveMode.Overwrite).saveAsTable("lkl_card_score.fqz_black_related_data2")
  }
  //黑名单关联边数据（针对实体属性标注）
  //fqz_black_related_data1
  //fqz_black_related_data2
  //黑合同数据(手工标注)
  //fqz_black_order_data1
  //fqz_black_order_data2
  //全量,6月20号重新导出
  //fqz_blackorder0608_case_data1_0620.csv
  //fqz_blackorder0608_case_data2_0620.csv
  //中介案件关联数据
  //fqz_intermediary_case_data1
  //fqz_intermediary_case_data2
  //中介案件关联数据脱敏数据
  //fqz_intermediary_case_data1_new
  //fqz_intermediary_case_data2_new
  //201608到201702所有的合同
  //fqz_order1608to1702_case_data1
  //fqz_order1608to1702_case_data2
  //fqz_order1608to1702_case_data2_2

  //scala 画图工具
  def draw(): Unit ={

  }

  def main(args: Array[String]): Unit = {
    args(0) match {
      case "loadFS2Hive" =>
        val Array(_,path,tableName) = args
        if(args.length > 3){
          val Array(_,_,_,format) = args
          loadFS2Hive(path,tableName,format)
        }else
          loadFS2Hive(path,tableName)
      case "JDBC2FS" =>
        if(args.length == 7){
          val Array(_,host,user,password,db,table,outputPath) = args
          JDBC2FS(host ,user,password,db,table,outputPath)
        }
        else if(args.length == 8){
          val Array(_,host,port,user,password,db,table,outputPath) = args
          JDBC2FS(host ,user,password,db,table,outputPath,port)
        }
        else if(args.length == 11){
          val Array(_,host,user,password,db,table,outputPath,colName,lowerBound,upperBound,numPartitions) = args
          JDBC2FS(host ,user,password,db,table,outputPath,"3306",colName,lowerBound.toLong,upperBound.toLong,numPartitions.toInt)
        }
      case "hive2FS" =>
        val Array(_,table,outputPath) = args
        hive2FS(table,outputPath)
      case "loadFS2JDBC" =>
        if(args.length == 4){
          val Array(_,url,table,inputPath) = args
          loadFS2JDBC(url,table,inputPath)
        }
        else if(args.length == 7){
          val Array(_,host,user,password,db,table,inputPath) = args
          loadFS2JDBC(host ,user,password,db,table,inputPath)
        }
        else if(args.length == 8){
          val Array(_,host,port,user,password,db,table,inputPath) = args
          loadFS2JDBC(host ,user,password,db,table,inputPath,port)
        }
    }
  }
}
