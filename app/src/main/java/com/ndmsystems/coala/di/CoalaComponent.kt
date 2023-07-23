package com.ndmsystems.coala.di

import com.ndmsystems.coala.Coala
import com.ndmsystems.coala.crypto.CurveRepository
import dagger.Component
import javax.inject.Singleton

/**
 * Created by Владимир on 06.07.2017.
 */
@Component(modules = [CoalaModule::class])
@Singleton
interface CoalaComponent {
    fun provideCurveRepository(): CurveRepository?
    fun inject(coala: Coala?)
}