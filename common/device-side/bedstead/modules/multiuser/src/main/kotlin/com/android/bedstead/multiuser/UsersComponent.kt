/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.multiuser

import android.os.UserManager
import android.util.Log
import com.android.bedstead.harrier.AnnotationExecutorUtil
import com.android.bedstead.harrier.Defaults
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.OtherUser
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.types.OptionalBoolean
import com.android.bedstead.nene.users.UserBuilder
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.users.UserType
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.ShellCommand
import com.google.common.base.Objects
import java.time.Duration
import org.junit.Assume
import org.junit.AssumptionViolatedException

/**
 * contains state and logic for managing users in context of DeviceState
 * this class shouldn't be used by tests directly
 */
class UsersComponent(private val deviceState: DeviceState) : DeviceStateComponent {

    private val context = context().instrumentedContext()
    private val createdUsers: MutableList<UserReference> = mutableListOf()
    private val mRemovedUsers: MutableList<RemovedUser> = mutableListOf()
    private var mOriginalSwitchedUser: UserReference? = null
    private var mAdditionalUser: UserReference? = null
    private var mAnnotationHasSwitchedUser = false
    private val mUsers: MutableMap<UserType, UserReference> = HashMap()
    private val mUsersSetPasswords: MutableList<UserReference> = mutableListOf()
    private var otherUserType: com.android.bedstead.harrier.UserType? = null

    /**
     * Remove the user and record the change
     */
    fun removeAndRecordUser(userReference: UserReference?) {
        if (userReference == null) {
            return
        }
        switchFromUser(userReference)
        if (!createdUsers.remove(userReference)) {
            mRemovedUsers.add(
                RemovedUser(
                    users().createUser()
                            .name(userReference.name())
                            .type(userReference.type())
                            .parent(userReference.parent()),
                    userReference.isRunning(),
                    Objects.equal(mOriginalSwitchedUser, userReference)
                )
            )
        }
        userReference.remove()
    }

    private fun switchFromUser(user: UserReference) {
        val currentUser = users().current()
        if (currentUser != user) {
            return
        }

        // We need to find a different user to switch to
        // full users only, starting with lowest ID
        val users = users().all().sortedBy { it.id() }
        for (otherUser in users) {
            if (otherUser == user) {
                continue
            }
            if (otherUser.parent() != null) {
                continue
            }
            if (!otherUser.isRunning()) {
                continue
            }
            if (!otherUser.canBeSwitchedTo()) {
                continue
            }
            switchToUser(otherUser)
            return
        }

        // There are no users to switch to so we'll create one.
        // In HSUM, an additional user needs to be created to switch from the existing user.
        ensureHasAdditionalUser(
            installInstrumentedApp = OptionalBoolean.ANY,
            switchedToUser = OptionalBoolean.TRUE
        )
    }

    private fun switchToUser(user: UserReference) {
        val currentUser = users().current()
        if (currentUser != user) {
            if (mOriginalSwitchedUser == null) {
                mOriginalSwitchedUser = currentUser
            }
            user.switchTo()
        }
    }

    /**
     * Ensure switched to the specified user
     */
    fun ensureSwitchedToUser(switchedToUser: OptionalBoolean, user: UserReference) {
        if (switchedToUser == OptionalBoolean.TRUE) {
            mAnnotationHasSwitchedUser = true
            switchToUser(user)
        } else if (switchedToUser == OptionalBoolean.FALSE) {
            mAnnotationHasSwitchedUser = true
            switchFromUser(user)
        }
    }

    /**
     * Returns the additional user specified by annotation
     */
    fun additionalUser(): UserReference = checkNotNull(mAdditionalUser) {
        "No additional user found. Ensure the correct annotations " +
                "have been used to declare use of additional user."
    }

    /**
     * Ensure has a user with a specified userType
     */
    fun ensureHasUser(
        userType: String,
        installInstrumentedApp: OptionalBoolean,
        switchedToUser: OptionalBoolean
    ) {
        val resolvedUserType: UserType = requireUserSupported(userType, FailureMode.SKIP)
        val user = users()
                .findUsersOfType(resolvedUserType).firstOrNull() ?: createUser(resolvedUserType)
        user.start()
        if (installInstrumentedApp == OptionalBoolean.TRUE) {
            packages().find(context.getPackageName()).installExisting(user)
        } else if (installInstrumentedApp == OptionalBoolean.FALSE) {
            packages().find(context.getPackageName()).uninstall(user)
        }
        ensureSwitchedToUser(switchedToUser, user)
        mUsers[resolvedUserType] = user
    }

    /**
     * Get a user of the given type.
     *
     * This should only be used to get users managed by Harrier (using either the
     * annotations or calls to the [DeviceState] class.
     *
     * @throws IllegalStateException if there is no harrier-managed user of the correct type
     */
    fun user(userType: String): UserReference {
        val resolvedUserType = users().supportedType(userType)
            ?: throw IllegalStateException(("Can not have a user of type " + userType +
                    " as they are not supported on this device"))
        return user(resolvedUserType)
    }

    /**
     * Get a user of the given type.
     *
     * This should only be used to get users managed by Harrier (using either the
     * annotations or calls to the [DeviceState] class.
     *
     * @throws IllegalStateException if there is no harrier-managed user of the correct type
     */
    fun user(userType: UserType): UserReference {
        return mUsers.getOrElse(userType) {
            throw IllegalStateException(
                "No harrier-managed user of type $userType. This method should only be used " +
                        "when Harrier has been used to create the user."
            )
        }
    }

    /**
     * Ensure the system doesn't contain any additional user
     */
    fun ensureHasNoAdditionalUser() {
        var additionalUser = additionalUserOrNull()
        while (additionalUser != null) {
            if (users().instrumented() == additionalUser) {
                throw AssumptionViolatedException(
                    "Tests with @EnsureHasNoAdditionalUser cannot run on an additional user"
                )
            }
            ensureSwitchedToUser(OptionalBoolean.FALSE, additionalUser)
            additionalUser.remove()
            additionalUser = additionalUserOrNull()
        }
        mAdditionalUser = null
    }

    private fun additionalUserOrNull(): UserReference? {
        val users = users()
                .findUsersOfType(users().supportedType(UserType.SECONDARY_USER_TYPE_NAME))
                .sortedBy { it.id() }
        return if (users().isHeadlessSystemUserMode) {
            users.drop(1).firstOrNull()
        } else {
            users.firstOrNull()
        }
    }

    /**
     * Ensure the system contains an additional user
     */
    fun ensureHasAdditionalUser(
        installInstrumentedApp: OptionalBoolean,
        switchedToUser: OptionalBoolean
    ) {
        if (users().isHeadlessSystemUserMode()) {
            val resolvedUserType: UserType = requireUserSupported(
                UserType.SECONDARY_USER_TYPE_NAME,
                FailureMode.SKIP
            )
            val users: Collection<UserReference> = users().findUsersOfType(resolvedUserType)
            if (users.size < 2) {
                createUser(resolvedUserType)
            }
            mAdditionalUser = additionalUserOrNull()
            if (installInstrumentedApp == OptionalBoolean.TRUE) {
                packages().find(context.getPackageName()).installExisting(mAdditionalUser)
            } else if (installInstrumentedApp == OptionalBoolean.FALSE) {
                packages().find(context.getPackageName()).uninstall(mAdditionalUser)
            }
            ensureSwitchedToUser(switchedToUser, mAdditionalUser!!)
        } else {
            ensureHasUser(UserType.SECONDARY_USER_TYPE_NAME, installInstrumentedApp, switchedToUser)
            mAdditionalUser = additionalUserOrNull()
        }
    }

    /**
     * Require the specified userType to be supported by the system
     */
    fun requireUserSupported(userType: String, failureMode: FailureMode): UserType {
        val resolvedUserType = users().supportedType(userType)
        AnnotationExecutorUtil.checkFailOrSkip(
            "Device must support user type $userType only supports: " +
                    users().supportedTypes(),
            resolvedUserType != null,
            failureMode
        )
        return resolvedUserType!!
    }

    /**
     * Ensure that there is a room for at least 1 additional user
     */
    fun ensureCanAddUser() {
        ensureCanAddUser(1, FailureMode.SKIP)
    }

    /**
     * Ensure that there is a room for additional users
     * @param number of users to add
     */
    fun ensureCanAddUser(number: Int, failureMode: FailureMode?) {
        val maxUsers: Int = getMaxNumberOfUsersSupported()
        val currentUsers = users().all().size

        // TODO(scottjonathan): Try to remove users until we have space - this will have to take
        // into account other users which have been added during the setup of this test.
        AnnotationExecutorUtil.checkFailOrSkip(
            "The device does not have space for $number additional user(s) " +
                    "($currentUsers current users, $maxUsers max users)",
            currentUsers + number <= maxUsers,
            failureMode
        )
    }

    /**
     * Ensure that there is no user of the given type.
     */
    fun ensureHasNoUser(userType: String) {
        val resolvedUserType = users().supportedType(userType)
            ?: // These user types don't exist so there can't be any
            return
        for (secondaryUser: UserReference in users().findUsersOfType(resolvedUserType)) {
            if (secondaryUser == users().instrumented()) {
                throw AssumptionViolatedException(
                    "This test only runs on devices without a $userType user. " +
                            "But the instrumented user is $userType"
                )
            }
            removeAndRecordUser(secondaryUser)
        }
    }

    /**
     * Create a user with a specified userType and parent
     */
    fun createUser(userType: UserType, parent: UserReference? = null): UserReference {
        deviceState.ensureDoesNotHaveUserRestriction(
            UserManager.DISALLOW_ADD_USER,
            com.android.bedstead.harrier.UserType.ANY
        )
        ensureCanAddUser()
        return try {
            val user = users().createUser()
                    .type(userType)
                    .parent(parent)
                    .createAndStart()
            createdUsers.add(user)
            user
        } catch (e: NeneException) {
            throw IllegalStateException("Error creating user of type $userType", e)
        }
    }

    private fun getMaxNumberOfUsersSupported(): Int {
        return try {
            ShellCommand.builder("pm get-max-users")
                    .validate { output: String ->
                        output.startsWith("Maximum supported users:")
                    }
                    .executeAndParseOutput { output: String ->
                        output.split(": ".toRegex(), limit = 2).toTypedArray()[1]
                                .trim { it <= ' ' }
                                .toInt()
                    }
        } catch (e: AdbException) {
            throw IllegalStateException("Invalid command output", e)
        }
    }

    fun requireRunOnAdditionalUser(switchedToUser: OptionalBoolean) {
        requireRunOnUser(arrayOf(UserType.SECONDARY_USER_TYPE_NAME), switchedToUser)
        if (users().isHeadlessSystemUserMode()) {
            if (users().instrumented() == users().initial()) {
                throw AssumptionViolatedException(
                    "This test requires running on an additional secondary user"
                )
            }
        }
        mAdditionalUser = additionalUserOrNull()
    }

    fun requireRunOnUser(userTypes: Array<String>, switchedToUser: OptionalBoolean) {
        var mutableSwitchedToUser = switchedToUser
        val instrumentedUser = users().instrumented()
        Assume.assumeTrue(
            "This test only runs on users of type " + userTypes.contentToString(),
            userTypes.any { it == instrumentedUser.type().name() }
        )
        mUsers[instrumentedUser.type()] = instrumentedUser
        if (mutableSwitchedToUser == OptionalBoolean.ANY) {
            if (instrumentedUser.isVisibleBagroundNonProfileUser()) {
                // If the option for a visible background user is ANY,
                // set it to FALSE to prevent user switching on the driver screen.
                mutableSwitchedToUser = OptionalBoolean.FALSE
            } else if (!mAnnotationHasSwitchedUser && instrumentedUser.canBeSwitchedTo()) {
                mutableSwitchedToUser = OptionalBoolean.TRUE
            }
        }
        if (mutableSwitchedToUser == OptionalBoolean.TRUE && !instrumentedUser.canBeSwitchedTo()) {
            if (users().isHeadlessSystemUserMode() && instrumentedUser == users().system()) {
                throw IllegalStateException(
                    "Cannot switch to system user on headless devices. " +
                            "Either add @RequireNotHeadlessSystemUserMode, or specify " +
                            "switchedToUser=ANY"
                )
            } else {
                throw IllegalStateException(
                    "Not permitted to switch to user " +
                            instrumentedUser + "(" + instrumentedUser.getSwitchToUserError() + ")"
                )
            }
        }
        ensureSwitchedToUser(mutableSwitchedToUser, instrumentedUser)
    }

    fun ensurePasswordSet(forUser: com.android.bedstead.harrier.UserType, password: String) {
        val user: UserReference = deviceState.resolveUserTypeToUser(forUser)
        if (user.hasLockCredential() && user.lockCredentialEquals(password)) {
            return
        }
        try {
            user.setPassword(password)
        } catch (e: NeneException) {
            throw AssertionError("Require password set but error when setting " +
                    "password on user " + user, e)
        }
        mUsersSetPasswords.add(user)
    }

    fun ensurePasswordNotSet(forUser: com.android.bedstead.harrier.UserType) {
        val user = deviceState.resolveUserTypeToUser(forUser)
        if (!user.hasLockCredential()) {
            return
        }
        if (mUsersSetPasswords.contains(user)) {
            try {
                user.clearPassword()
            } catch (e: NeneException) {
                Log.e(LOG_TAG, "Error clearing password", e)
            }
        }
        if (!user.hasLockCredential()) {
            return
        }
        try {
            user.clearPassword(Defaults.DEFAULT_PASSWORD)
        } catch (exception: NeneException) {
            throw AssertionError(
                "Test requires user " + user + " does not have a password. " +
                        "Password is set and is not DEFAULT_PASSWORD.",
                exception
            )
        }
        mUsersSetPasswords.remove(user)
    }

    private fun clearPasswords() {
        mUsersSetPasswords.forEach {
            if (!createdUsers.contains(it)) { // Will be removed anyway
                it.clearPassword()
            }
        }
        mUsersSetPasswords.clear()
    }

    override fun teardownShareableState() {
        clearPasswords()
        var ephemeralUser: UserReference? = null
        val currentUser = users().current()
        for (user in createdUsers) {
            try {
                if (user == currentUser) {
                    // user will be removed after switching to mOriginalSwitchedUser below.
                    user.removeWhenPossible()
                    ephemeralUser = user
                } else {
                    user.remove()
                }
            } catch (e: NeneException) {
                if (user.exists()) {
                    // Otherwise it's probably just already removed
                    throw NeneException("Could not remove user", e)
                }
            }
        }

        createdUsers.clear()

        clearRemovedUsers()
        mOriginalSwitchedUser?.let { originalSwitchedUser ->
            if (!originalSwitchedUser.exists()) {
                Log.d(
                    LOG_TAG,
                    "Could not switch back to original user " + originalSwitchedUser +
                            " as it does not exist. Switching to initial instead."
                )
                users().initial().switchTo()
            } else {
                originalSwitchedUser.switchTo()
            }
            mOriginalSwitchedUser = null

            // wait for ephemeral user to be removed after being switched away
            if (ephemeralUser != null) {
                Poll.forValue("Ephemeral user exists") { ephemeralUser.exists() }
                        .toBeEqualTo(false)
                        .timeout(Duration.ofMinutes(1))
                        .errorOnFail()
                        .await()
            }
        }
    }

    private fun clearRemovedUsers() {
        for (removedUser in mRemovedUsers) {
            val user = removedUser.userBuilder.create()
            if (removedUser.isRunning) {
                user.start()
            }
            if (removedUser.isOriginalSwitchedToUser) {
                mOriginalSwitchedUser = user
            }
        }

        mRemovedUsers.clear()
    }

    override fun teardownNonShareableState() {
        mUsers.clear()
        mAnnotationHasSwitchedUser = false
        mAdditionalUser = null
        otherUserType = null
    }

    override fun prepareTestState() {
        if (mOriginalSwitchedUser == null) {
            mOriginalSwitchedUser = users().current()
        }
    }

    /**
     * See [OtherUser]
     */
    fun handleOtherUser(userType: com.android.bedstead.harrier.UserType) {
        otherUserType = userType
    }

    /**
     * See [com.android.bedstead.harrier.DeviceState.otherUser]
     */
    fun otherUser(): UserReference {
        checkNotNull(otherUserType) { "No other user specified. Use @OtherUser" }
        return deviceState.resolveUserTypeToUser(otherUserType)
    }

    companion object {
        private const val LOG_TAG = "DeviceStateUsers"
    }
}

private class RemovedUser(
    val userBuilder: UserBuilder,
    val isRunning: Boolean,
    val isOriginalSwitchedToUser: Boolean
)

/**
 * See [UsersComponent.user]
 */
fun DeviceState.user(userType: String): UserReference {
    return getDependency(UsersComponent::class.java).user(userType)
}
