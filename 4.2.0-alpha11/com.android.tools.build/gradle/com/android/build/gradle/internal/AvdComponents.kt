/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal

import com.android.SdkConstants
import com.android.build.gradle.internal.services.ServiceRegistrationAction
import com.android.sdklib.FileOpFileWrapper
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.internal.avd.AvdCamera
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.EmulatedProperties
import com.android.sdklib.internal.avd.GpuMode
import com.android.sdklib.internal.avd.HardwareProperties
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.ILogger
import com.android.utils.StdLogger
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.io.File
import javax.inject.Inject

/**
 * Build Service for loading and creating Android Virtual Devices.
 */
abstract class AvdComponentsBuildService @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory
) :
        BuildService<AvdComponentsBuildService.Parameters> {

    interface Parameters : BuildServiceParameters {
        val sdkService: Property<SdkComponentsBuildService>
        val avdLocation: Property<String>
    }

    private val sdkDirectory: File
    get() = parameters.sdkService.get().sdkDirectoryProvider.get().asFile

    private val sdkHandler: AndroidSdkHandler by lazy {
        AndroidSdkHandler.getInstance(sdkDirectory)
    }

    private val logger: ILogger = LoggerWrapper.getLogger(AvdComponentsBuildService::class.java)

    private val avdManager: AvdManager by lazy {
        AvdManager.getInstance(
            sdkHandler,
            avdFolder,
            logger
        ) ?: throw RuntimeException ("Failed to initialize AvdManager")
    }

    private val deviceManager: DeviceManager by lazy {
        DeviceManager.createInstance(sdkDirectory, logger)
    }

    private val avdFolder: File by lazy {
        // TODO(b/16526279): Make sure this is wired up correctly once build service is set up.
        File(parameters.avdLocation.get())
    }
    
    private fun createOrRetrieveAvd(
        imageHash: String,
        deviceName: String,
        hardwareProfile: String
    ): File {
        val info = avdManager.getAvd(deviceName, false)
        if (info != null) {
            // already generated the avd
            return info.configFile
        }

        val imageProvider = parameters.sdkService.get().sdkImageDirectoryProvider(imageHash)
        if (!imageProvider.isPresent) {
            throw RuntimeException("Failed to find system image for hash: $imageHash")
        }
        val imageLocation = imageProvider.get().asFile
        val systemImage = sdkHandler.getSystemImageManager(
            LoggerProgressIndicatorWrapper(StdLogger(StdLogger.Level.VERBOSE))
        ).getImageAt(imageLocation)

        systemImage?: throw RuntimeException("System image does not exist at $imageLocation")

        val device = deviceManager.getDevices(DeviceManager.ALL_DEVICES).find {
            it.displayName == hardwareProfile
        } ?: throw RuntimeException("Failed to find hardware profile for name: $hardwareProfile")

        val hardwareConfig = defaultHardwareConfig()
        hardwareConfig.putAll(DeviceManager.getHardwareProperties(device))
        EmulatedProperties.restrictDefaultRamSize(hardwareConfig)

        val deviceFolder = AvdInfo.getDefaultAvdFolder(
            avdManager, deviceName, sdkHandler.fileOp, false)

        val newInfo = avdManager.createAvd(
            deviceFolder,
            deviceName,
            systemImage,
            null,
            null,
            null,
            hardwareConfig,
            device.bootProps,
            device.hasPlayStore(),
            false,
            false,
            logger
        )
        return parameters.sdkService.get().sdkImageDirectoryProvider(imageHash).get().asFile
    }

    private fun defaultHardwareConfig(): MutableMap<String, String> {
        // Get the defaults of all the user-modifiable properties.
        val emulatorPackage = sdkHandler.getLocalPackage(
            SdkConstants.FD_EMULATOR,
            LoggerProgressIndicatorWrapper(StdLogger(StdLogger.Level.VERBOSE))
        )

        if (emulatorPackage == null) {
            // TODO(b/16526279) Should download emulator package
            error ("AVD Emulator package is not downloaded. Failed to retrieve hardware defaults" +
                    "for virtual device.")
        }

        val libDirectory = File(emulatorPackage.location, SdkConstants.FD_LIB)
        val hardwareDefs = File(libDirectory, SdkConstants.FN_HARDWARE_INI)
        val hwMap =
                HardwareProperties.parseHardwareDefinitions(
                    FileOpFileWrapper(hardwareDefs, sdkHandler.fileOp, false), logger)

        val hwConfigMap = defaultEmulatorPropertiesMap.toMutableMap()

        hwMap.values.forEach {
            val default = it.default
            if (!default.isNullOrEmpty()) {
                hwConfigMap[it.name] = default
            }
        }

        return hwConfigMap
    }

    // TODO(b/166641485): Move to a utilites class. Map is pulled from AvdManagerCli
    private val defaultEmulatorPropertiesMap: Map<String, String> =
        mapOf(
            EmulatedProperties.BACK_CAMERA_KEY to AvdCamera.EMULATED.asParameter,
            EmulatedProperties.CPU_CORES_KEY
                    to EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES.toString(),
            EmulatedProperties.CUSTOM_SKIN_FILE_KEY to "_no_skin",
            EmulatedProperties.DEVICE_FRAME_KEY to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.FRONT_CAMERA_KEY to AvdCamera.EMULATED.asParameter,
            EmulatedProperties.HAS_HARDWARE_KEYBOARD_KEY to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.HOST_GPU_MODE_KEY to GpuMode.AUTO.gpuSetting,
            HardwareProperties.HW_INITIAL_ORIENTATION to "Portrait",
            EmulatedProperties.INTERNAL_STORAGE_KEY
                    to EmulatedProperties.DEFAULT_INTERNAL_STORAGE.toString(),
            EmulatedProperties.NETWORK_LATENCY_KEY to "None",
            EmulatedProperties.NETWORK_SPEED_KEY to "Full",
            EmulatedProperties.SDCARD_SIZE to EmulatedProperties.DEFAULT_SDCARD_SIZE.toString(),
            EmulatedProperties.USE_CHOSEN_SNAPSHOT_BOOT to HardwareProperties.BOOLEAN_NO,
            EmulatedProperties.USE_COLD_BOOT to HardwareProperties.BOOLEAN_NO,
            EmulatedProperties.USE_FAST_BOOT to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.USE_HOST_GPU_KEY to HardwareProperties.BOOLEAN_YES,
            EmulatedProperties.VM_HEAP_STORAGE_KEY to EmulatedProperties.DEFAULT_HEAP.toString())

    fun avdProvider(
        imageHash: String,
        deviceName: String,
        hardwareProfile: String
    ): Provider<Directory> =
        objectFactory.directoryProperty().fileProvider(providerFactory.provider {
            createOrRetrieveAvd(imageHash, deviceName, hardwareProfile)
        })

    class RegistrationAction(
        project: Project,
        private val avdFolderLocation: String,
        private val sdkService: Provider<SdkComponentsBuildService>
    ) : ServiceRegistrationAction<AvdComponentsBuildService, Parameters>(
        project,
        AvdComponentsBuildService::class.java
    ) {

        override fun configure(parameters: Parameters) {
            parameters.avdLocation.set(avdFolderLocation)
            parameters.sdkService.set(sdkService)
        }
    }

}
