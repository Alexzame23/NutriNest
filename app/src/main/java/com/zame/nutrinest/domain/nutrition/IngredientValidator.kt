package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.data.local.entity.NutrientTags
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Checks that a user-entered ingredient is nutritionally possible.
 *
 * The rules mirror the ones the shipped food dataset ran over its own 936 foods
 * (its `food_quality_issues` codes are reused verbatim), so a hand-typed
 * ingredient is held to the same standard as an imported one and the detail
 * screen can render both kinds of warning through a single code path.
 *
 * Pure and Android-free, like [NutritionCalculator], so it unit-tests directly.
 * Messages are not produced here — the UI maps [ValidationCode] to a string
 * resource, which keeps the validator translatable.
 */
object IngredientValidator {

    enum class Severity {
        /** Physically impossible. Saving is blocked. */
        ERROR,

        /** Suspicious but conceivable. Saving is allowed after confirmation. */
        WARNING,

        /** Worth mentioning; never blocks. */
        INFO
    }

    enum class ValidationCode {
        NAME_REQUIRED,
        CATEGORY_REQUIRED,
        KCAL_REQUIRED,
        KCAL_INVALID,
        CARBS_INVALID,
        FATS_INVALID,
        PROTEIN_INVALID,
        NUTRIENT_NEGATIVE,
        GRAMS_EXCEED_100,
        MASS_BALANCE_OUT_OF_RANGE,
        ENERGY_MISMATCH,
        SATFAT_EXCEEDS_FAT,
        SUGARS_EXCEED_CARBS,
        STARCH_EXCEEDS_CARBS,
        KCAL_IMPLAUSIBLE,
        SERVING_SIZE_INVALID,
        PORTION_SIZE_INVALID,
        DENSITY_INVALID,
        SPARSE_NUTRIENT_COVERAGE,

        /** Raised by the editor's ViewModel, which owns the database lookup. */
        DUPLICATE_NAME,
        SIMILAR_NAME
    }

    /** Which form field an issue points at, so the editor can highlight it. */
    enum class Field {
        NAME, CATEGORY, KCAL, CARBS, FATS, PROTEIN, SERVING_SIZE, PORTION, DENSITY, MICRONUTRIENT
    }

    data class Issue(
        val code: ValidationCode,
        val severity: Severity,
        val field: Field? = null,
        /** Filled in for issues about one nutrient, e.g. "FASAT". */
        val tag: String? = null,
        /** Numbers the message interpolates, already rounded for display. */
        val args: List<String> = emptyList()
    )

    data class Result(val issues: List<Issue>) {
        val errors: List<Issue> get() = issues.filter { it.severity == Severity.ERROR }
        val warnings: List<Issue> get() = issues.filter { it.severity == Severity.WARNING }
        val canSave: Boolean get() = errors.isEmpty()
        val needsConfirmation: Boolean get() = warnings.isNotEmpty()
        fun fieldsInError(): Set<Field> = errors.mapNotNull { it.field }.toSet()
    }

    /**
     * The values the editor collects, all per 100 g.
     * [extraNutrients] is keyed by nutrient tag so new nutrients need no changes here.
     */
    data class Draft(
        val name: String,
        val categoryKey: String?,
        val kcal: Double?,
        val carbs: Double?,
        val fats: Double?,
        val protein: Double?,
        val extraNutrients: Map<String, Double> = emptyMap(),
        val servingSizeG: Double? = null,
        /** Only non-empty portion rows; null means its number did not parse. */
        val portionGrams: List<Double?> = emptyList(),
        val densityGml: Double? = null
    )

    /** Energy factors, EU Reg. 1169/2011 Annex XIV — the dataset's own basis. */
    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val KCAL_PER_G_FIBRE = 2.0
    private const val KCAL_PER_G_ALCOHOL = 7.0

    /** Pure fat is 884 kcal/100 g, so anything beyond ~900 is a typo. */
    private const val MAX_KCAL_PER_100G = 900.0
    private const val ENERGY_TOLERANCE = 0.15
    private const val MASS_TOLERANCE_G = 2.0

    fun validate(draft: Draft): Result {
        val issues = mutableListOf<Issue>()

        if (draft.name.isBlank()) {
            issues += Issue(ValidationCode.NAME_REQUIRED, Severity.ERROR, Field.NAME)
        }
        if (draft.categoryKey.isNullOrBlank()) {
            issues += Issue(ValidationCode.CATEGORY_REQUIRED, Severity.ERROR, Field.CATEGORY)
        }

        val kcal = draft.kcal
        val carbs = draft.carbs
        val fats = draft.fats
        val protein = draft.protein

        if (kcal == null) {
            issues += Issue(ValidationCode.KCAL_REQUIRED, Severity.ERROR, Field.KCAL)
        } else if (!kcal.isFinite() || kcal < 0) {
            issues += Issue(ValidationCode.KCAL_INVALID, Severity.ERROR, Field.KCAL)
        } else if (kcal > MAX_KCAL_PER_100G) {
            issues += Issue(
                ValidationCode.KCAL_IMPLAUSIBLE, Severity.ERROR, Field.KCAL,
                args = listOf(MAX_KCAL_PER_100G.format0())
            )
        }

        issues += checkGramField(carbs, ValidationCode.CARBS_INVALID, Field.CARBS)
        issues += checkGramField(fats, ValidationCode.FATS_INVALID, Field.FATS)
        issues += checkGramField(protein, ValidationCode.PROTEIN_INVALID, Field.PROTEIN)

        draft.extraNutrients.forEach { (tag, value) ->
            if (!value.isFinite() || value < 0) {
                issues += Issue(
                    ValidationCode.NUTRIENT_NEGATIVE, Severity.ERROR,
                    Field.MICRONUTRIENT, tag
                )
            }
        }

        draft.servingSizeG?.let {
            if (!it.isFinite() || it <= 0 || it > 5_000) {
                issues += Issue(ValidationCode.SERVING_SIZE_INVALID, Severity.ERROR, Field.SERVING_SIZE)
            }
        }
        if (draft.portionGrams.any { it == null || !it.isFinite() || it <= 0 || it > 5_000 }) {
            issues += Issue(ValidationCode.PORTION_SIZE_INVALID, Severity.ERROR, Field.PORTION)
        }
        draft.densityGml?.let {
            // Honey is ~1.42, oils ~0.91. Outside 0.1–2.5 is not food.
            if (!it.isFinite() || it <= 0.1 || it > 2.5) {
                issues += Issue(ValidationCode.DENSITY_INVALID, Severity.ERROR, Field.DENSITY)
            }
        }

        // The cross-field rules below only make sense once the macros parse.
        if (carbs == null || fats == null || protein == null ||
            carbs < 0 || fats < 0 || protein < 0
        ) {
            return Result(issues)
        }

        val fibre = draft.extraNutrients[NutrientTags.FIBRE]?.coerceAtLeast(0.0) ?: 0.0
        val sugars = draft.extraNutrients[NutrientTags.SUGARS]
        val starch = draft.extraNutrients[NutrientTags.STARCH]
        val satFat = draft.extraNutrients[NutrientTags.SAT_FAT]
        val alcohol = draft.extraNutrients[NutrientTags.ALCOHOL]?.coerceAtLeast(0.0) ?: 0.0
        val water = draft.extraNutrients[NutrientTags.WATER]?.coerceAtLeast(0.0) ?: 0.0

        // Nothing can weigh more than the 100 g it is measured in. The dataset
        // states carbohydrate excludes fibre, so the two add up independently.
        val mass = carbs + fats + protein + fibre + alcohol + water
        if (mass > 100.0 + MASS_TOLERANCE_G) {
            issues += Issue(
                ValidationCode.MASS_BALANCE_OUT_OF_RANGE, Severity.ERROR,
                args = listOf(mass.format1())
            )
        }

        if (satFat != null && satFat > fats + 0.05) {
            issues += Issue(
                ValidationCode.SATFAT_EXCEEDS_FAT, Severity.ERROR,
                Field.MICRONUTRIENT, NutrientTags.SAT_FAT,
                listOf(satFat.format1(), fats.format1())
            )
        }
        if (sugars != null && sugars > carbs + 0.05) {
            issues += Issue(
                ValidationCode.SUGARS_EXCEED_CARBS, Severity.ERROR,
                Field.MICRONUTRIENT, NutrientTags.SUGARS,
                listOf(sugars.format1(), carbs.format1())
            )
        }
        if (starch != null && starch > carbs + 0.05) {
            issues += Issue(
                ValidationCode.STARCH_EXCEEDS_CARBS, Severity.ERROR,
                Field.MICRONUTRIENT, NutrientTags.STARCH,
                listOf(starch.format1(), carbs.format1())
            )
        }

        // Calories should follow from the macros. A mismatch usually means a
        // digit slipped, so warn rather than block — some foods legitimately
        // report measured energy that differs from the Atwater estimate.
        if (kcal != null && kcal >= 0 && kcal <= MAX_KCAL_PER_100G) {
            val computed = atwaterKcal(protein, carbs, fats, fibre, alcohol)
            if (computed > 20.0 && abs(kcal - computed) / computed > ENERGY_TOLERANCE) {
                issues += Issue(
                    ValidationCode.ENERGY_MISMATCH, Severity.WARNING, Field.KCAL,
                    args = listOf(kcal.format0(), computed.format0())
                )
            }
        }

        return Result(issues)
    }

    /** Energy from macros, per 100 g, on the dataset's own basis. */
    fun atwaterKcal(
        protein: Double,
        carbs: Double,
        fats: Double,
        fibre: Double = 0.0,
        alcohol: Double = 0.0
    ): Double =
        protein * KCAL_PER_G_PROTEIN +
            carbs * KCAL_PER_G_CARBS +
            fats * KCAL_PER_G_FAT +
            fibre * KCAL_PER_G_FIBRE +
            alcohol * KCAL_PER_G_ALCOHOL

    private fun checkGramField(
        value: Double?,
        invalidCode: ValidationCode,
        field: Field
    ): List<Issue> = when {
        value == null || !value.isFinite() || value < 0 ->
            listOf(Issue(invalidCode, Severity.ERROR, field))
        value > 100.0 -> listOf(Issue(ValidationCode.GRAMS_EXCEED_100, Severity.ERROR, field))
        else -> emptyList()
    }

    private fun Double.format0() = round(this).toInt().toString()
    private fun Double.format1() = String.format(Locale.US, "%.1f", this)
}
