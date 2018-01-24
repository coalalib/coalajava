package com.ndmsystems.coala.di;

import com.ndmsystems.coala.Coala;
import com.ndmsystems.coala.crypto.CurveRepository;

import javax.inject.Singleton;

import dagger.Component;
import dagger.Provides;

/**
 * Created by Владимир on 06.07.2017.
 */

@Component(modules = {CoalaModule.class})
@Singleton
public interface CoalaComponent {

    public CurveRepository provideCurveRepository();

    void inject(Coala coala);
}
