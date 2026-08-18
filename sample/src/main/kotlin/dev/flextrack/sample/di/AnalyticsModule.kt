package dev.flextrack.sample.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.flextrack.config.FlexTrackConfigurationBuilder
import dev.flextrack.routing.ConsentState
import dev.flextrack.logging.FlexTrackLogLevel
import dev.flextrack.runtime.FlexTrackClient
import dev.flextrack.sample.analytics.ReliableDemoTracker
import dev.flextrack.sample.analytics.RetryDemoTracker
import dev.flextrack.sample.data.DemoPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    @Singleton
    fun provideClient(
        @ApplicationContext context: Context,
        preferences: DemoPreferences,
        reliableTracker: ReliableDemoTracker,
        retryTracker: RetryDemoTracker,
    ): FlexTrackClient = FlexTrackConfigurationBuilder(context).apply {
        tracker(reliableTracker)
        tracker(retryTracker)
        persistentQueue()
        consent { ConsentState(general = preferences.consentNow()) }
        network(preferences::onlineNow)
        routing {
            defineGroup("sample-destinations", "sample_reliable", "sample_retry")
            routeNamed("sample_delivery_lab") {
                toGroup("sample-destinations")
                skipConsent()
                priority(100)
                id("delivery-lab")
            }
            routeDefault {
                toTracker("sample_reliable")
                id("sample-default")
            }
        }
        // The sample intentionally exposes payload values to demonstrate debugging.
        // AndroidLogcatLogger still disables itself for non-debuggable builds.
        logging(FlexTrackLogLevel.VERBOSE)
    }.buildUnstarted()
}
