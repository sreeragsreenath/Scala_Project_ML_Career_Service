/**
  * The Naive Bayes App to train the machine learning model
  *
  * @author  Sreerag Mandakathil Sreenath
  * @version 1.0
  * @since   2019-04-17
  */


// Importing Libraries
import org.apache.spark.SparkConf
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.NaiveBayes
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.feature._
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.log4j.{Level, Logger}


object NaiveBayesTextClassifier extends App {

  Logger.getLogger("org").setLevel(Level.OFF)
  Logger.getLogger("akka").setLevel(Level.OFF)

  // Defining the configuration for Spark
  val conf = new SparkConf()
  conf.setMaster("local")
  conf.setAppName("NaiveBayes Text classifier")

  // Starting Spark session
  val spark = SparkSession
    .builder()
    .appName("NaiveBayes Text classifier")
    .config(conf)
    .getOrCreate()


  // Specifying the location of the data file
  val dataFile = "src/main/scala/data/indeed_11-04-2019.json"

  // Loading the data file into a data frame
  val df = spark.read.json(dataFile)

  val dfSmall = df.select("job_title", "job_posting_desc")
  // Defining processes for the pipeline
  val indexer = new StringIndexer().setInputCol("job_title").setOutputCol("label")
  val tokenizer = new Tokenizer().setInputCol("job_posting_desc").setOutputCol("tokens")
  val hashingTF = new HashingTF().setInputCol("tokens").setOutputCol("features")
  val nb = new NaiveBayes().setLabelCol("label").setFeaturesCol("features")
  var reverseIndexer = indexer.fit(dfSmall).labels
  val labelReverse = new IndexToString().setInputCol("prediction").setLabels(reverseIndexer).setOutputCol("predicted_job_title")

  val array_string = reverseIndexer.mkString(",")
  println(array_string)
  val Array(trainingData, testData) = dfSmall.randomSplit(Array(0.7, 0.3), seed = 11L)

  // Creating a pipeline for the modelling
  val pipeline = new Pipeline().setStages(Array(indexer,tokenizer,hashingTF, nb, labelReverse))

  // Splitting into training and testing data from the main data frame

  println("Training Data")
  trainingData.show()
  println("Testing Data")
  testData.show()
  val model = pipeline.fit(trainingData)

  val prediction = model.transform(testData)
  prediction.show()

  prediction.select("label","job_title","prediction", "predicted_job_title")
    .write.mode(SaveMode.Overwrite).format("json").save("src/main/scala/classifier/spark-prediction")

  prediction.write.mode(SaveMode.Overwrite).format("json").save("src/main/scala/classifier/spark-prediction-full")

  val predictions = model.transform(testData)
  val evaluatorRF = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("prediction").setMetricName("accuracy")
  val accuracy = evaluatorRF.evaluate(predictions)

  // Prints the accuracy of the model
  println(f"Accuracy = $accuracy%.4f")

  // Saving model to classifier folder
  model.write.overwrite().save("src/main/scala/classifier/spark-model")

  spark.stop()

}
