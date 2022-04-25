package com.rocketinsights.android.di.hilt

import com.rocketinsights.android.network.RecipeService
import com.rocketinsights.android.network.models.recipe_hilt.RecipeDtoMapper
import com.rocketinsights.android.repos.RecipeRepository
import com.rocketinsights.android.repos.RecipeRepositoryImplementation
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideRecipeRepository(
        recipeService: RecipeService,
        recipeMapper: RecipeDtoMapper,
    ): RecipeRepository {
        return RecipeRepositoryImplementation(
            recipeService = recipeService,
            mapper = recipeMapper
        )
    }
}
