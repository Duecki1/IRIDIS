package com.dueckis.kawaiiraweditor.data.model

import java.util.Locale

internal enum class AiEnvironmentCategory(val id: String, val label: String) {
    People("people", "People"),
    Animals("animals", "Animals"),
    Plants("plants", "Plants"),
    Food("food", "Food"),
    Vehicles("vehicles", "Vehicles"),
    Trains("trains", "Trains"),
    Cars("cars", "Cars"),
    Planes("planes", "Planes"),
    Sky("sky", "Sky"),
    Water("water", "Water"),
    Floor("floor", "Floor"),
    Dogs("dogs", "Dogs"),
    Cats("cats", "Cats");

    companion object {
        fun fromId(id: String?): AiEnvironmentCategory {
            if (id.isNullOrBlank()) return Sky
            val normalized = id.lowercase(Locale.US)

            val legacyMapped =
                when (normalized) {
                    "architecture" -> Floor.id
                    "old-buildings" -> Floor.id
                    "humans" -> People.id
                    "kids" -> People.id
                    "buildings" -> Floor.id
                    "floor" -> Floor.id
                    "ground" -> Floor.id
                    "roads" -> Floor.id
                    "sidewalks" -> Floor.id
                    "dogs" -> Dogs.id
                    "cats" -> Cats.id
                    else -> normalized
                }

            return entries.firstOrNull { it.id == legacyMapped } ?: Sky
        }
    }
}
