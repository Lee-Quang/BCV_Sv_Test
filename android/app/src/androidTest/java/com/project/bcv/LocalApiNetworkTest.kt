package com.project.bcv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalApiNetworkTest {
    @Test
    fun localApiIsReachableOnWifi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val response = JsonApiClient(context, BuildConfig.API_BASE_URL).get("health", "")
        assertEquals("ok", response.getString("status"))
    }

    @Test
    fun registrationRequestReachesBackend() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val body = JSONObject()
            .put("email", "not-an-email")
            .put("password", "Testpass123")
            .put("confirmPassword", "Testpass123")
        try {
            JsonApiClient(context, BuildConfig.API_BASE_URL).post("api/account/register", body)
            fail("Backend should reject an invalid email")
        } catch (error: ApiFailure) {
            assertEquals("INVALID_EMAIL", error.code)
        }
    }
}
