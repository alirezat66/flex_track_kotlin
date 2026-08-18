package dev.flextrack.sample.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.flextrack.routing.RoutingConfiguration
import dev.flextrack.routing.ConsentState
import dev.flextrack.logging.AndroidLogcatLogger
import dev.flextrack.logging.FlexTrackLogLevel
import dev.flextrack.routing.RoutingEngine
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import dev.flextrack.runtime.FileEventQueue
import dev.flextrack.runtime.FlexTrackClient
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
    ): FlexTrackClient = FlexTrackClient(
        routingEngine = RoutingEngine(
            RoutingConfiguration(
                rules = listOf(
                    RoutingRule(
                        id = "delivery-lab",
                        eventNameContains = "sample_delivery_lab",
                        targetGroup = TrackerGroup(
                            "sample-destinations",
                            listOf("sample_reliable", "sample_retry"),
                        ),
                        requireConsent = false,
                        priority = 100,
                    ),
                    RoutingRule(
                        id = "sample-default",
                        isDefault = true,
                        targetGroup = TrackerGroup(
                            "sample-default-destination",
                            listOf("sample_reliable"),
                        ),
                        requireConsent = true,
                        priority = 0,
                    ),
                ),
            ),
        ),
        queue = FileEventQueue(context),
        consentProvider = { ConsentState(general = preferences.consentNow()) },
        onlineProvider = preferences::onlineNow,
        // The sample intentionally exposes payload values to demonstrate debugging.
        // AndroidLogcatLogger still disables itself for non-debuggable builds.
        logger = AndroidLogcatLogger(context, FlexTrackLogLevel.VERBOSE),
    )
}
