// Databricks notebook source
// STARTER CODE - DO NOT EDIT THIS CELL
import org.apache.spark.sql.functions.desc
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import spark.implicits._
import org.apache.spark.sql.expressions.Window

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
val customSchema = StructType(Array(StructField("lpep_pickup_datetime", StringType, true), StructField("lpep_dropoff_datetime", StringType, true), StructField("PULocationID", IntegerType, true), StructField("DOLocationID", IntegerType, true), StructField("passenger_count", IntegerType, true), StructField("trip_distance", FloatType, true), StructField("fare_amount", FloatType, true), StructField("payment_type", IntegerType, true)))

// COMMAND ----------

// STARTER CODE - YOU CAN LOAD ANY FILE WITH A SIMILAR SYNTAX.
val df = spark.read
   .format("com.databricks.spark.csv")
   .option("header", "true") // Use first line of all files as header
   .option("nullValue", "null")
   .schema(customSchema)
   .load("/FileStore/tables/nyc_tripdata.csv") // the csv file which you want to work with
   .withColumn("pickup_datetime", from_unixtime(unix_timestamp(col("lpep_pickup_datetime"), "MM/dd/yyyy HH:mm")))
   .withColumn("dropoff_datetime", from_unixtime(unix_timestamp(col("lpep_dropoff_datetime"), "MM/dd/yyyy HH:mm")))
   .drop($"lpep_pickup_datetime")
   .drop($"lpep_dropoff_datetime")

// COMMAND ----------

// LOAD THE "taxi_zone_lookup.csv" FILE SIMILARLY AS ABOVE. CAST ANY COLUMN TO APPROPRIATE DATA TYPE IF NECESSARY.

// ENTER THE CODE BELOW
val customSchema2 = StructType(Array(StructField("LocationID", IntegerType, true), StructField("Borough", StringType, true), StructField("Zone", StringType, true), StructField("service_zone", StringType, true)))

val dfTaxi = spark.read
   .format("com.databricks.spark.csv")
   .option("header", "true") // Use first line of all files as header
   .option("nullValue", "null")
   .schema(customSchema2)
   .load("/FileStore/tables/taxi_zone_lookup.csv")


// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
// Some commands that you can use to see your dataframes and results of the operations. You can comment the df.show(5) and uncomment display(df) to see the data differently. You will find these two functions useful in reporting your results.
// display(df)
df.show(5) // view the first 5 rows of the dataframe

// COMMAND ----------

// STARTER CODE - DO NOT EDIT THIS CELL
// Filter the data to only keep the rows where "PULocationID" and the "DOLocationID" are different and the "trip_distance" is strictly greater than 2.0 (>2.0).

// VERY VERY IMPORTANT: ALL THE SUBSEQUENT OPERATIONS MUST BE PERFORMED ON THIS FILTERED DATA

val df_filter = df.filter($"PULocationID" =!= $"DOLocationID" && $"trip_distance" > 2.0)
df_filter.show(5)

// COMMAND ----------

// PART 1a: The top-5 most popular drop locations - "DOLocationID", sorted in descending order - if there is a tie, then one with lower "DOLocationID" gets listed first
// Output Schema: DOLocationID int, number_of_dropoffs int 

// Hint: Checkout the groupBy(), orderBy() and count() functions.

// ENTER THE CODE BELOW

val df_oneAp = df_filter.groupBy("DOLocationID").count()
val df_oneAm = df_oneAp.orderBy(col("count").desc, col("DOLocationID").asc)
val df_oneA = df_oneAm.withColumnRenamed("count", "number_of_dropoffs")

df_oneA.show(5)

// COMMAND ----------

// PART 1b: The top-5 most popular pickup locations - "PULocationID", sorted in descending order - if there is a tie, then one with lower "PULocationID" gets listed first 
// Output Schema: PULocationID int, number_of_pickups int

// Hint: Code is very similar to part 1a above.

// ENTER THE CODE BELOW

val df_oneBp = df_filter.groupBy("PULocationID").count()
val df_oneBr = df_oneBp.orderBy(col("count").desc, col("PULocationID").asc)
val df_oneB = df_oneBr.withColumnRenamed("count", "number_of_pickups")

df_oneB.show(5)

// COMMAND ----------

// PART 2: List the top-3 locations with the maximum overall activity, i.e. sum of all pickups and all dropoffs at that LocationID. In case of a tie, the lower LocationID gets listed first.
// Output Schema: LocationID int, number_activities int

// Hint: In order to get the result, you may need to perform a join operation between the two dataframes that you created in earlier parts (to come up with the sum of the number of pickups and dropoffs on each location). 


val joined = df_oneA.join(df_oneB, df_oneA("DOLocationID") === df_oneB("PULocationID"), "inner")

val addedVal = joined.withColumn("number_activities", col("number_of_dropoffs") + col("number_of_pickups"))

val partTT = addedVal.drop("number_of_dropoffs").drop("number_of_pickups").drop("PULocationID")

val partTTT = partTT.withColumnRenamed("DOLocationID", "LocationID")

val partT = partTTT.orderBy(col("number_activities").desc, col("LocationID").asc)

partT.show(3)


// ENTER THE CODE BELOW


// COMMAND ----------

// PART 3: List all the boroughs in the order of having the highest to lowest number of activities (i.e. sum of all pickups and all dropoffs at that LocationID), along with the total number of activity counts for each borough in NYC during that entire period of time.
// Output Schema: Borough string, total_number_activities int

// Hint: You can use the dataframe obtained from the previous part, and will need to do the join with the 'taxi_zone_lookup' dataframe. Also, checkout the "agg" function applied to a grouped dataframe.

val prepart = partT.withColumnRenamed("LocationID", "loc")

val joining = dfTaxi.join(prepart, dfTaxi("LocationID") === prepart("loc"), "inner").drop("loc").drop("service_zone").drop("LocationID")

val summed = joining.groupBy("Borough").agg(sum("number_activities").as("total_number_activities")).toDF()

val partth = summed.orderBy(col("total_number_activities").desc).show()

// ENTER THE CODE BELOW


// COMMAND ----------

// PART 4: List the top 2 days of week with the largest number of (daily) average pickups, along with the values of average number of pickups on each of the two days. The day of week should be a string with its full name, for example, "Monday" - not a number 1 or "Mon" instead.
// Output Schema: day_of_week string, avg_count float

// Hint: You may need to group by the "date" (without time stamp - time in the day) first. Checkout "to_date" function.

var testM = df_filter.withColumn("collapsedDate", to_date(col("pickup_datetime"), "yyyy-MM-dd"))

val groupByDate = testM.groupBy("collapsedDate").count()

var wdays = groupByDate.withColumn("collapsedDate", date_format(col("collapsedDate"), "EEEE")).toDF()

var avgDays = wdays.groupBy("collapsedDate").agg(avg("count")).orderBy(col("avg(count)").desc)

var partFour = avgDays.withColumnRenamed("avg(count)", "avg_count")

partFour.show(2)

// ENTER THE CODE BELOW


// COMMAND ----------

// PART 5: For each particular hour of a day (0 to 23, 0 being midnight) - in their order from 0 to 23, find the zone in Brooklyn borough with the LARGEST number of pickups. 
// Output Schema: hour_of_day int, zone string, max_count int

// Hint: You may need to use "Window" over hour of day, along with "group by" to find the MAXIMUM count of pickups

var bolb = df_filter.withColumn("hourOfDay", hour(col("pickup_datetime")))

var joinT = dfTaxi.join(bolb, dfTaxi("LocationID") === bolb("PULocationID"), "inner").drop("DOLocationID").drop("service_zone").drop("fare_amount").drop("payment_type").drop("passenger_count").drop("trip_distance").drop("pickup_datetime").drop("dropoff_datetime").drop("PULocationID")

var collaBor = joinT.groupBy("Borough", "Zone", "hourOfDay").count()

var filt = collaBor.filter($"Borough" === "Brooklyn").drop("Borough")

var wm = Window.partitionBy("hourOfDay").orderBy("hourOfDay")

var filtm = filt.drop("Zone")

var lime = filtm.withColumn("row", row_number.over(wm)).withColumn("maxC", max("count").over(wm)).where(col("row") === 1).drop("row").drop("count")

var lih = lime.orderBy("hourOfDay")

var filted = filt.withColumnRenamed("hourOfDay", "HOD")

var joinMer = lih.join(filted, (lih("maxC") === filted("count")) and (lih("hourOfDay") === filted("HOD")), "left").drop("maxC").drop("HOD")

var partFi = joinMer.withColumnRenamed("count", "max_count").orderBy("hourOfDay").show(25)


// var biler = filt.groupBy("hourOfDay").agg(first("Zone"), max("count"))

// val partFi = biler.orderBy(col("hourOfDay").asc)

// val partFiv = partFi.withColumnRenamed("first(Zone, false)", "zone").show(25)



// COMMAND ----------



// PART 6 - Find which 3 different days of the January, in Manhattan, saw the largest percentage increment in pickups compared to previous day, in the order from largest increment % to smallest increment %. 
// Print the day of month along with the percent CHANGE (can be negative), rounded to 2 decimal places, in number of pickups compared to previous day.
// Output Schema: day int, percent_change float


// Hint: You might need to use lag function, over a window ordered by day of month.

// ENTER THE CODE BELOW

var getY = df_filter.withColumn("yearN", date_format(col("pickup_datetime"), "yyyy"))

var onlyY = getY.filter($"yearN" === 2019)

var getJan = onlyY.withColumn("monthNum", date_format(col("pickup_datetime"), "MMMM"))

var onlyJan = getJan.filter($"monthNum" === "January")

var joinWT = dfTaxi.join(onlyJan, dfTaxi("LocationID") === onlyJan("PULocationID"), "inner").drop("service_zone").drop("fare_amount").drop("payment_type").drop("DOLocationID").drop("PULocationID").drop("Zone").drop("dropoff_datetime").drop("monthNum").drop("trip_distance").drop("passenger_count").drop("LocationID")

var manhat = joinWT.filter($"Borough" === "Manhattan")

var dateCon = manhat.withColumn("dateGroup", to_date(col("pickup_datetime"), "yyyy-MM-dd")).drop("pickup_datetime")

var daycoll = dateCon.groupBy("dateGroup").count()

val dorde = daycoll.orderBy(col("dateGroup").asc)

val w = Window.orderBy("dateGroup")

val wtest = dorde.withColumn("prevDay", lag("count", 1).over(w))

val ddif = wtest.withColumn("delta", col("count") - col("prevDay"))

val chang = ddif.withColumn("relCh", col("delta") / col("prevDay") * 100)

var addDay = chang.withColumn("day", date_format(col("dateGroup"), "d"))

val partSo = addDay.withColumn("percent_change", round(col("relCh"), 2)).drop("dateGroup").drop("count").drop("prevDay").drop("delta").drop("relCh")

val partSi = partSo.orderBy(col("percent_change").desc).show(3)



// COMMAND ----------


