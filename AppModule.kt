package com.pontoface.di

import android.content.Context
import androidx.room.Room
import com.pontoface.BuildConfig
import com.pontoface.camera.CameraSource
import com.pontoface.camera.MockCameraSource
import com.pontoface.camera.RealCameraSource
import com.pontoface.data.PontoDatabase
import com.pontoface.data.PontoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Injeta MockCameraSource em DEBUG (USE_MOCK_CAMERA=true)
     * ou RealCameraSource em RELEASE automaticamente.
     */
    @Provides
    @Singleton
    fun provideCameraSource(
        mockCamera: MockCameraSource,
        realCamera: RealCameraSource
    ): CameraSource {
        return if (BuildConfig.USE_MOCK_CAMERA) {
            mockCamera
        } else {
            realCamera
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PontoDatabase {
        return Room.databaseBuilder(
            context,
            PontoDatabase::class.java,
            "ponto_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideRepository(db: PontoDatabase): PontoRepository {
        return PontoRepository(db.registroDao())
    }
}
