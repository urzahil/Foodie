package rocks.urzahil.foodie

import android.app.Application
import android.util.Log
import rocks.urzahil.foodie.data.local.AppDatabase
import rocks.urzahil.foodie.data.repository.RestaurantRepository
import com.google.android.gms.maps.MapsInitializer

class FoodieApp : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var repository: RestaurantRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = RestaurantRepository(this, database.restaurantDao())

        try {
            MapsInitializer.initialize(
                applicationContext,
                MapsInitializer.Renderer.LATEST
            ) { renderer ->
                Log.d("FoodieApp", "Maps initialized with renderer: $renderer")
            }
        } catch (e: Exception) {
            Log.e("FoodieApp", "Maps initialization error", e)
        }
    }
}
