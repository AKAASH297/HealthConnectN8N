package xyz.angeloanan.healthconnectexports

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import kotlin.reflect.KClass

val httpClient = HttpClient(Android)

// All Health Connect record types
val allRecordTypes: List<KClass<out Record>> = listOf(
    // Activity
    ActiveCaloriesBurnedRecord::class,
    DistanceRecord::class,
    ElevationGainedRecord::class,
    ExerciseSessionRecord::class,
    FloorsClimbedRecord::class,
    PowerRecord::class,
    SpeedRecord::class,
    StepsRecord::class,
    StepsCadenceRecord::class,
    TotalCaloriesBurnedRecord::class,
    WheelchairPushesRecord::class,
    CyclingPedalingCadenceRecord::class,
    Vo2MaxRecord::class,

    // Body Measurements
    BasalMetabolicRateRecord::class,
    BodyFatRecord::class,
    BodyWaterMassRecord::class,
    BoneMassRecord::class,
    HeightRecord::class,
    LeanBodyMassRecord::class,
    WeightRecord::class,

    // Vitals
    BasalBodyTemperatureRecord::class,
    BloodGlucoseRecord::class,
    BloodPressureRecord::class,
    BodyTemperatureRecord::class,
    HeartRateRecord::class,
    HeartRateVariabilityRmssdRecord::class,
    OxygenSaturationRecord::class,
    RespiratoryRateRecord::class,
    RestingHeartRateRecord::class,

    // Cycle Tracking
    CervicalMucusRecord::class,
    IntermenstrualBleedingRecord::class,
    MenstruationFlowRecord::class,
    MenstruationPeriodRecord::class,
    OvulationTestRecord::class,
    SexualActivityRecord::class,

    // Nutrition
    HydrationRecord::class,
    NutritionRecord::class,

    // Sleep
    SleepSessionRecord::class,
)

val requiredHealthConnectPermissions = allRecordTypes.map { 
    HealthPermission.getReadPermission(it) 
}.toSet()

class DataExporterScheduleWorker(
    appContext: Context, workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val notificationManager = applicationContext.getSystemService<NotificationManager>()!!
    private val healthConnect = HealthConnectClient.getOrCreate(applicationContext)
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    private fun createNotificationChannel(): NotificationChannel {
        val notificationChannel = NotificationChannel(
            "export",
            "Data export",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationChannel.description = "Shown when Health Connect data is being exported"
        notificationChannel.enableLights(false)
        notificationChannel.enableVibration(false)

        notificationManager.createNotificationChannel(notificationChannel)
        return notificationChannel
    }

    private fun createExceptionNotification(e: Exception): Notification {
        return NotificationCompat.Builder(applicationContext, "export")
            .setContentTitle("Export failed")
            .setContentText("Failed to export Health Connect data")
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    private suspend fun isHealthConnectPermissionGranted(healthConnect: HealthConnectClient): Boolean {
        val grantedPermissions = healthConnect.permissionController.getGrantedPermissions()
        // Check if at least some permissions are granted (user might not have all data types)
        return grantedPermissions.isNotEmpty()
    }

    private suspend fun <T : Record> readRecordsOfType(
        recordType: KClass<T>,
        startTime: Instant,
        endTime: Instant
    ): List<T> {
        return try {
            val response = healthConnect.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.w("DataExporterWorker", "Failed to read ${recordType.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private fun recordToMap(record: Record): Map<String, Any?> {
        val baseMap = mutableMapOf<String, Any?>(
            "type" to record::class.simpleName,
            "metadata" to mapOf(
                "id" to record.metadata.id,
                "dataOrigin" to record.metadata.dataOrigin.packageName,
                "lastModifiedTime" to record.metadata.lastModifiedTime.toString(),
                "recordingMethod" to record.metadata.recordingMethod
            )
        )

        when (record) {
            // Activity Records
            is ActiveCaloriesBurnedRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["energy_kcal"] = record.energy.inKilocalories
            }
            is DistanceRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["distance_meters"] = record.distance.inMeters
            }
            is ElevationGainedRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["elevation_meters"] = record.elevation.inMeters
            }
            is ExerciseSessionRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["exerciseType"] = record.exerciseType
                baseMap["title"] = record.title
                baseMap["notes"] = record.notes
                baseMap["segments"] = record.segments.map { segment ->
                    mapOf(
                        "startTime" to segment.startTime.toString(),
                        "endTime" to segment.endTime.toString(),
                        "segmentType" to segment.segmentType
                    )
                }
                baseMap["laps"] = record.laps.map { lap ->
                    mapOf(
                        "startTime" to lap.startTime.toString(),
                        "endTime" to lap.endTime.toString(),
                        "length_meters" to lap.length?.inMeters
                    )
                }
            }
            is FloorsClimbedRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["floors"] = record.floors
            }
            is PowerRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["samples"] = record.samples.map { sample ->
                    mapOf(
                        "time" to sample.time.toString(),
                        "power_watts" to sample.power.inWatts
                    )
                }
            }
            is SpeedRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["samples"] = record.samples.map { sample ->
                    mapOf(
                        "time" to sample.time.toString(),
                        "speed_metersPerSecond" to sample.speed.inMetersPerSecond
                    )
                }
            }
            is StepsRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["count"] = record.count
            }
            is StepsCadenceRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["samples"] = record.samples.map { sample ->
                    mapOf(
                        "time" to sample.time.toString(),
                        "rate_stepsPerMinute" to sample.rate
                    )
                }
            }
            is TotalCaloriesBurnedRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["energy_kcal"] = record.energy.inKilocalories
            }
            is WheelchairPushesRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["count"] = record.count
            }
            is CyclingPedalingCadenceRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["samples"] = record.samples.map { sample ->
                    mapOf(
                        "time" to sample.time.toString(),
                        "revolutionsPerMinute" to sample.revolutionsPerMinute
                    )
                }
            }
            is Vo2MaxRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["vo2MillilitersPerMinuteKilogram"] = record.vo2MillilitersPerMinuteKilogram
                baseMap["measurementMethod"] = record.measurementMethod
            }

            // Body Measurements
            is BasalMetabolicRateRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["basalMetabolicRate_kcalPerDay"] = record.basalMetabolicRate.inKilocaloriesPerDay
            }
            is BodyFatRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["percentage"] = record.percentage.value
            }
            is BodyWaterMassRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["mass_kg"] = record.mass.inKilograms
            }
            is BoneMassRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["mass_kg"] = record.mass.inKilograms
            }
            is HeightRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["height_meters"] = record.height.inMeters
            }
            is LeanBodyMassRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["mass_kg"] = record.mass.inKilograms
            }
            is WeightRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["weight_kg"] = record.weight.inKilograms
            }

            // Vitals
            is BasalBodyTemperatureRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["temperature_celsius"] = record.temperature.inCelsius
                baseMap["measurementLocation"] = record.measurementLocation
            }
            is BloodGlucoseRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["level_mmolPerL"] = record.level.inMillimolesPerLiter
                baseMap["specimenSource"] = record.specimenSource
                baseMap["mealType"] = record.mealType
                baseMap["relationToMeal"] = record.relationToMeal
            }
            is BloodPressureRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["systolic_mmHg"] = record.systolic.inMillimetersOfMercury
                baseMap["diastolic_mmHg"] = record.diastolic.inMillimetersOfMercury
                baseMap["bodyPosition"] = record.bodyPosition
                baseMap["measurementLocation"] = record.measurementLocation
            }
            is BodyTemperatureRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["temperature_celsius"] = record.temperature.inCelsius
                baseMap["measurementLocation"] = record.measurementLocation
            }
            is HeartRateRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["samples"] = record.samples.map { sample ->
                    mapOf(
                        "time" to sample.time.toString(),
                        "beatsPerMinute" to sample.beatsPerMinute
                    )
                }
            }
            is HeartRateVariabilityRmssdRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["heartRateVariabilityMillis"] = record.heartRateVariabilityMillis
            }
            is OxygenSaturationRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["percentage"] = record.percentage.value
            }
            is RespiratoryRateRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["rate_breathsPerMinute"] = record.rate
            }
            is RestingHeartRateRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["beatsPerMinute"] = record.beatsPerMinute
            }

            // Cycle Tracking
            is CervicalMucusRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["appearance"] = record.appearance
                baseMap["sensation"] = record.sensation
            }
            is IntermenstrualBleedingRecord -> {
                baseMap["time"] = record.time.toString()
            }
            is MenstruationFlowRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["flow"] = record.flow
            }
            is MenstruationPeriodRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
            }
            is OvulationTestRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["result"] = record.result
            }
            is SexualActivityRecord -> {
                baseMap["time"] = record.time.toString()
                baseMap["protectionUsed"] = record.protectionUsed
            }

            // Nutrition
            is HydrationRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["volume_liters"] = record.volume.inLiters
            }
            is NutritionRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["name"] = record.name
                baseMap["mealType"] = record.mealType
                baseMap["energy_kcal"] = record.energy?.inKilocalories
                baseMap["protein_grams"] = record.protein?.inGrams
                baseMap["totalCarbohydrate_grams"] = record.totalCarbohydrate?.inGrams
                baseMap["totalFat_grams"] = record.totalFat?.inGrams
                baseMap["saturatedFat_grams"] = record.saturatedFat?.inGrams
                baseMap["unsaturatedFat_grams"] = record.unsaturatedFat?.inGrams
                baseMap["transFat_grams"] = record.transFat?.inGrams
                baseMap["cholesterol_grams"] = record.cholesterol?.inGrams
                baseMap["dietaryFiber_grams"] = record.dietaryFiber?.inGrams
                baseMap["sugar_grams"] = record.sugar?.inGrams
                baseMap["sodium_grams"] = record.sodium?.inGrams
                baseMap["potassium_grams"] = record.potassium?.inGrams
                baseMap["calcium_grams"] = record.calcium?.inGrams
                baseMap["iron_grams"] = record.iron?.inGrams
                baseMap["vitaminA_grams"] = record.vitaminA?.inGrams
                baseMap["vitaminC_grams"] = record.vitaminC?.inGrams
                baseMap["vitaminD_grams"] = record.vitaminD?.inGrams
                baseMap["vitaminE_grams"] = record.vitaminE?.inGrams
                baseMap["vitaminK_grams"] = record.vitaminK?.inGrams
                baseMap["vitaminB6_grams"] = record.vitaminB6?.inGrams
                baseMap["vitaminB12_grams"] = record.vitaminB12?.inGrams
                baseMap["folate_grams"] = record.folate?.inGrams
                baseMap["thiamin_grams"] = record.thiamin?.inGrams
                baseMap["riboflavin_grams"] = record.riboflavin?.inGrams
                baseMap["niacin_grams"] = record.niacin?.inGrams
                baseMap["biotin_grams"] = record.biotin?.inGrams
                baseMap["pantothenicAcid_grams"] = record.pantothenicAcid?.inGrams
                baseMap["phosphorus_grams"] = record.phosphorus?.inGrams
                baseMap["iodine_grams"] = record.iodine?.inGrams
                baseMap["magnesium_grams"] = record.magnesium?.inGrams
                baseMap["zinc_grams"] = record.zinc?.inGrams
                baseMap["selenium_grams"] = record.selenium?.inGrams
                baseMap["copper_grams"] = record.copper?.inGrams
                baseMap["manganese_grams"] = record.manganese?.inGrams
                baseMap["chromium_grams"] = record.chromium?.inGrams
                baseMap["molybdenum_grams"] = record.molybdenum?.inGrams
                baseMap["chloride_grams"] = record.chloride?.inGrams
                baseMap["caffeine_grams"] = record.caffeine?.inGrams
            }

            // Sleep
            is SleepSessionRecord -> {
                baseMap["startTime"] = record.startTime.toString()
                baseMap["endTime"] = record.endTime.toString()
                baseMap["title"] = record.title
                baseMap["notes"] = record.notes
                baseMap["stages"] = record.stages.map { stage ->
                    mapOf(
                        "startTime" to stage.startTime.toString(),
                        "endTime" to stage.endTime.toString(),
                        "stage" to stage.stage
                    )
                }
            }

            else -> {
                baseMap["rawData"] = record.toString()
            }
        }

        return baseMap
    }

    override suspend fun doWork(): Result {
        val notificationChannel = createNotificationChannel()

        Log.d("DataExporterWorker", "Checking exports prerequisites")
        val isGranted = isHealthConnectPermissionGranted(healthConnect)

        if (!isGranted) {
            Log.d("DataExporterWorker", "Health Connect permissions not granted")
            return Result.failure()
        }
        Log.d("DataExporterWorker", "✅ Health Connect permissions granted")

        val exportDestination: String? =
            applicationContext.dataStore.data.map { it[EXPORT_DESTINATION_URI] }.first()
        if (exportDestination == null) {
            Log.d("DataExporterWorker", "Export destination not set")
            return Result.failure()
        }
        Log.d("DataExporterWorker", "✅ Export destination set")

        val foregroundNotification =
            NotificationCompat.Builder(applicationContext, notificationChannel.id)
                .setContentTitle("Exporting data")
                .setContentText("Exporting Health Connect data to the cloud")
                .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true)
                .build()

        notificationManager.notify(1, foregroundNotification)

        // Time range: previous calendar week (Monday to Sunday)
        val zoneId = TimeZone.getDefault().toZoneId()
        val today = LocalDate.now(zoneId)
        // Get the Monday of the current week, then go back 7 days to get last week's Monday
        val lastWeekMonday = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1)
        val lastWeekSunday = lastWeekMonday.plusDays(6)
        val startTime = lastWeekMonday.atStartOfDay(zoneId).toInstant()
        val endTime = lastWeekSunday.plusDays(1).atStartOfDay(zoneId).toInstant().minusMillis(1)

        Log.d("DataExporterWorker", "Fetching health data from $startTime to $endTime")

        val allData = mutableMapOf<String, List<Map<String, Any?>>>()
        val grantedPermissions = healthConnect.permissionController.getGrantedPermissions()

        // Fetch all record types
        for (recordType in allRecordTypes) {
            val permissionNeeded = HealthPermission.getReadPermission(recordType)
            if (permissionNeeded !in grantedPermissions) {
                Log.d("DataExporterWorker", "Skipping ${recordType.simpleName} - permission not granted")
                continue
            }

            try {
                Log.d("DataExporterWorker", "Reading ${recordType.simpleName}...")
                val records = readRecordsOfType(recordType, startTime, endTime)
                if (records.isNotEmpty()) {
                    val recordMaps = records.map { recordToMap(it) }
                    allData[recordType.simpleName ?: "Unknown"] = recordMaps
                    Log.d("DataExporterWorker", "✅ ${recordType.simpleName}: ${records.size} records")
                } else {
                    Log.d("DataExporterWorker", "⏭️ ${recordType.simpleName}: no records")
                }
            } catch (e: Exception) {
                Log.w("DataExporterWorker", "Failed to read ${recordType.simpleName}: ${e.message}")
            }
        }

        val exportPayload = mapOf(
            "exportTime" to Instant.now().toString(),
            "timeRangeStart" to startTime.toString(),
            "timeRangeEnd" to endTime.toString(),
            "recordTypes" to allData.keys.toList(),
            "totalRecords" to allData.values.sumOf { it.size },
            "data" to allData
        )

        val json = gson.toJson(exportPayload)
        Log.d("DataExporterWorker", "Total export size: ${json.length} chars, ${allData.values.sumOf { it.size }} records")

        try {
            Log.d("DataExporterWorker", "Exporting data to $exportDestination")
            httpClient.post("https://$exportDestination") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        } catch (e: Exception) {
            Log.e("DataExporterWorker", "Failed to export data", e)

            notificationManager.cancel(1)
            notificationManager.notify(1, createExceptionNotification(e))
            return Result.failure()
        }

        notificationManager.cancel(1)
        return Result.success()
    }
}