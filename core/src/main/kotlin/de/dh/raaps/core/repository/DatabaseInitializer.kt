package de.dh.raaps.core.repository

import android.content.Context
import de.dh.raaps.common.R
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.DEFAULT_BASAL_UNITS_PER_HOUR
import de.dh.raaps.common.model.DEFAULT_IC_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_BG_TARGET_MGDL
import de.dh.raaps.common.model.DEFAULT_BG_LOW_THRESHOLD_MGDL
import de.dh.raaps.common.model.ID_INSULIN_ASPART
import de.dh.raaps.common.model.ID_INSULIN_FIASP
import de.dh.raaps.common.model.ID_MEAL_FAST
import de.dh.raaps.common.model.ID_MEAL_HIGH_FAT
import de.dh.raaps.common.model.ID_MEAL_SLOW
import de.dh.raaps.common.model.ID_MEAL_STANDARD
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.CurrentSettings
import de.dh.raaps.common.model.data.CurrentTherapySettings
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.BgBlock

object DatabaseInitializer {
    suspend fun initialize(
        context: Context,
        treatmentRepository: TreatmentRepository,
        therapyRepository: TherapyRepository,
        settingsRepository: SettingsRepository
    ) {
        initializeInsulinTypes(context, treatmentRepository)
        initializeMealTypes(context, treatmentRepository)
        initializeDefaultInsulinProfileAndCurrentTherapy(context, therapyRepository)
        initializeSettings(settingsRepository)
    }

    private suspend fun initializeInsulinTypes(context: Context, repository: TreatmentRepository) {
        if (repository.getAllInsulinTypes().isNotEmpty()) {
            return
        }
        repository.insertInsulinType(
            InsulinType(
                id = ID_INSULIN_ASPART,
                name = context.getString(R.string.insulin_type_aspart_name),
                dia = Minutes.ofHours(5),
                peak = Minutes(75)
            )
        )
        repository.insertInsulinType(
            InsulinType(
                id = ID_INSULIN_FIASP,
                name = context.getString(R.string.insulin_type_fiasp_name),
                dia = Minutes.ofHours(4),
                peak = Minutes(55)
            )
        )
    }

    private suspend fun initializeMealTypes(context: Context, repository: TreatmentRepository) {
        if (repository.getAllMealTypes().isNotEmpty()) {
            return
        }
        repository.insertMealType(
            MealType(
                id = ID_MEAL_FAST,
                name = context.getString(R.string.meal_type_fast_carbs_name),
                components = listOf(
                    CarbCurveComponentData(weight = 100, peakMinutes = Minutes(25))
                ),
                cat = Minutes(90)
            )
        )
        repository.insertMealType(
            MealType(
                id = ID_MEAL_STANDARD,
                name = context.getString(R.string.meal_type_standard_meal_name),
                components = listOf(
                    CarbCurveComponentData(weight = 70, peakMinutes = Minutes(75)),
                    CarbCurveComponentData(weight = 30, peakMinutes = Minutes(150))
                ),
                cat = Minutes.ofHours(4)
            )
        )
        repository.insertMealType(
            MealType(
                id = ID_MEAL_HIGH_FAT,
                name = context.getString(R.string.meal_type_high_fat_meal_name),
                components = listOf(
                    CarbCurveComponentData(weight = 35, peakMinutes = Minutes(60)),
                    CarbCurveComponentData(weight = 65, peakMinutes = Minutes(240))
                ),
                cat = Minutes.ofHours(6)
            )
        )
        repository.insertMealType(
            MealType(
                id = ID_MEAL_SLOW,
                name = context.getString(R.string.meal_type_slow_meal_name),
                components = listOf(
                    CarbCurveComponentData(weight = 40, peakMinutes = Minutes(120)),
                    CarbCurveComponentData(weight = 60, peakMinutes = Minutes(300))
                ),
                cat = Minutes.ofHours(8)
            )
        )
    }

    private suspend fun initializeDefaultInsulinProfileAndCurrentTherapy(context: Context, repository: TherapyRepository) {
        val insulinTypes = repository.getAllInsulinTypes()
        if (insulinTypes.isEmpty()) return

        val defaultInsulinType = insulinTypes.first()

        var profiles = repository.getAllInsulinProfiles()
        if (profiles.isEmpty()) {
            val normalProfile = InsulinProfile(
                name = context.getString(R.string.profile_default_normal_name),
                basalBlocks = listOf(Block(
                    Minutes.ofHours(24),
                    DEFAULT_BASAL_UNITS_PER_HOUR
                )),
                isfBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_ISF_MGDL_PER_UNIT)),
                icBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_IC_GRAM_PER_UNIT)),
                insulinType = defaultInsulinType,
                dia = defaultInsulinType.dia,
                peak = defaultInsulinType.peak
            )
            repository.insertInsulinProfile(normalProfile)
            profiles = listOf(normalProfile)
        }

        if (repository.getCurrentTherapySettingsOrNull() == null) {
            val activeProfile = profiles.first()

            val currentTherapySettings = CurrentTherapySettings(
                insulinProfile = activeProfile,
                defaultBgBlocks = listOf(
                    BgBlock(
                        Minutes.ofHours(24),
                        BgValue.fromMgDl(DEFAULT_BG_TARGET_MGDL),
                        BgValue.fromMgDl(DEFAULT_BG_LOW_THRESHOLD_MGDL)
                    )
                )
            )
            repository.updateCurrentTherapySettings(currentTherapySettings)
        }
    }

    private suspend fun initializeSettings(repository: SettingsRepository) {
        if (repository.getCurrentSettings() == null) {
            repository.updateCurrentSettings(CurrentSettings())
        }
    }
}