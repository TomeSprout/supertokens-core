/*
 *    Copyright (c) 2022, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package io.supertokens.test.userIdMapping;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.authRecipe.AuthRecipe;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.nonAuthRecipe.NonAuthRecipeStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMappingStorage;
import io.supertokens.pluginInterface.useridmapping.exception.UnknownSuperTokensUserIdException;
import io.supertokens.pluginInterface.useridmapping.exception.UserIdMappingAlreadyExistsException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.useridmapping.UserIdMapping;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.usermetadata.UserMetadata;
import io.supertokens.webserver.WebserverAPI;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.reflections.Reflections;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public class UserIdMappingTest {
    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testCreatingUserIdMappingWithUnknownSuperTokensUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a userId mapping with unknown SuperTokens UserId
        Exception error = null;
        try {
            UserIdMapping.createUserIdMapping(process.main, "unknownSuperTokensUserId", "externalUserId", "someInfi",
                    false);
        } catch (Exception e) {
            error = e;
        }

        assertNotNull(error);
        assertTrue(error instanceof UnknownSuperTokensUserIdException);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDuplicateUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");

        String externalUserId = "external-test";

        UserIdMapping.createUserIdMapping(process.main, userInfo.id, externalUserId, null, false);

        {
            // duplicate exception with both supertokensUserId and externalUserId
            Exception error = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, userInfo.id, externalUserId, null, false);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UserIdMappingAlreadyExistsException);

            UserIdMappingAlreadyExistsException usersIdMappingExistsError = (UserIdMappingAlreadyExistsException) error;
            assertTrue(usersIdMappingExistsError.doesExternalUserIdExist);
            assertTrue(usersIdMappingExistsError.doesSuperTokensUserIdExist);
        }

        {
            // duplicate exception with superTokensUserId
            Exception error = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, userInfo.id, "newExternalId", null, false);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UserIdMappingAlreadyExistsException);

            UserIdMappingAlreadyExistsException usersIdMappingExistsError = (UserIdMappingAlreadyExistsException) error;
            assertFalse(usersIdMappingExistsError.doesExternalUserIdExist);
            assertTrue(usersIdMappingExistsError.doesSuperTokensUserIdExist);

        }

        {
            // duplicate exception with externalUserId

            UserInfo newUser = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
            Exception error = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, newUser.id, externalUserId, null, false);
            } catch (Exception e) {
                error = e;
            }

            assertNotNull(error);
            assertTrue(error instanceof UserIdMappingAlreadyExistsException);

            UserIdMappingAlreadyExistsException usersIdMappingExistsError = (UserIdMappingAlreadyExistsException) error;
            assertTrue(usersIdMappingExistsError.doesExternalUserIdExist);
            assertFalse(usersIdMappingExistsError.doesSuperTokensUserIdExist);

        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testCreatingUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);

        // create a user
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPassword");

        String externalUserId = "external-test";
        String externalUserIdInfo = "external-info";

        // create a userId mapping
        UserIdMapping.createUserIdMapping(process.getProcess(), userInfo.id, externalUserId, externalUserIdInfo, false);

        // check that the mapping exists
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = storage.getUserIdMapping(userInfo.id,
                true);
        assertEquals(userInfo.id, userIdMapping.superTokensUserId);
        assertEquals(externalUserId, userIdMapping.externalUserId);
        assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUseridMappingWithUnknownId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // get mapping with unknown userId with userIdType SUPERTOKENS
        assertNull(UserIdMapping.getUserIdMapping(process.main, "unknownUserId", UserIdType.SUPERTOKENS));

        // get mapping with unknown userId with userIdType EXTERNAL
        assertNull(UserIdMapping.getUserIdMapping(process.main, "unknownUserId", UserIdType.EXTERNAL));

        // get mapping with unknown userId with userIdTYPE ANY
        assertNull(UserIdMapping.getUserIdMapping(process.main, "unknownUserId", UserIdType.ANY));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testRetrievingUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a User and then a UserId mapping
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo, false);

        // retrieve mapping with supertokensUserId and validate response

        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(response);
            assertEquals(superTokensUserId, response.superTokensUserId);
            assertEquals(externalUserId, response.externalUserId);
            assertEquals(externalUserIdInfo, response.externalUserIdInfo);
        }

        // retrieve mapping externalUserId and validate response

        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL);

            assertNotNull(response);
            assertEquals(superTokensUserId, response.superTokensUserId);
            assertEquals(externalUserId, response.externalUserId);
            assertEquals(externalUserIdInfo, response.externalUserIdInfo);
        }

        // retrieve mapping with using ANY
        {
            {
                // with supertokensUserId
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.ANY);

                assertNotNull(response);
                assertEquals(superTokensUserId, response.superTokensUserId);
                assertEquals(externalUserId, response.externalUserId);
                assertEquals(externalUserIdInfo, response.externalUserIdInfo);
            }

            {
                // with externalUserId
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, externalUserId, UserIdType.ANY);

                assertNotNull(response);
                assertEquals(superTokensUserId, response.superTokensUserId);
                assertEquals(externalUserId, response.externalUserId);
                assertEquals(externalUserIdInfo, response.externalUserIdInfo);
            }
        }

        // create a new mapping where the superTokensUserId of Mapping1 = externalUserId of Mapping2
        UserInfo userInfo2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
        String newSuperTokensUserId = userInfo2.id;
        String newExternalUserId = userInfo.id;
        String newExternalUserIdInfo = "newExternalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, newSuperTokensUserId, newExternalUserId, newExternalUserIdInfo,
                true);

        // retrieve the mapping with newExternalUserId using ANY, it should return Mapping 1
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, newExternalUserId, UserIdType.ANY);

            // query with the storage layer and check that the db returns two entries
            UserIdMappingStorage storage = StorageLayer.getUserIdMappingStorage(process.main);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping[] storageResponse = storage
                    .getUserIdMapping(newExternalUserId);
            assertEquals(2, storageResponse.length);

            assertNotNull(response);
            assertEquals(superTokensUserId, response.superTokensUserId);
            assertEquals(externalUserId, response.externalUserId);
            assertEquals(externalUserIdInfo, response.externalUserIdInfo);
        }

        // retrieve the mapping with newExternalUserId using EXTERNAL, it should return Mapping 2
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, newExternalUserId, UserIdType.EXTERNAL);

            assertNotNull(response);
            assertEquals(newSuperTokensUserId, response.superTokensUserId);
            assertEquals(newExternalUserId, response.externalUserId);
            assertEquals(newExternalUserIdInfo, response.externalUserIdInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMappingWithUnknownId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // deleting a mapping with an unknown UserId with userIdType as SUPERTOKENS

        assertFalse(UserIdMapping.deleteUserIdMapping(process.main, "unknownUserId", UserIdType.SUPERTOKENS, false));

        // deleting a mapping with an unknown UserId with userIdType as EXTERNAL

        assertFalse(UserIdMapping.deleteUserIdMapping(process.main, "unknownUserId", UserIdType.EXTERNAL, false));

        // deleting a mapping with an unknown UserId with userIdType as ANY

        assertFalse(UserIdMapping.deleteUserIdMapping(process.main, "unknownUserId", UserIdType.ANY, false));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMapping() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create mapping and check that it exists
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalIdInfo";

        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_1 = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                superTokensUserId, externalUserId, externalUserIdInfo);

        {
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo,
                    false);

            // retrieve mapping and validate response

            {
                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

                assertNotNull(response);
                assertEquals(userIdMapping_1, response);
            }

            // Delete mapping with userIdType SUPERTOKENS and check that it is deleted
            assertTrue(
                    UserIdMapping.deleteUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS, false));

            // check that mapping does not exist
            assertNull(UserIdMapping.getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS));
        }

        {
            // create mapping and check that it exists
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo,
                    false);

            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL);

            assertNotNull(response);
            assertEquals(userIdMapping_1, response);

            // delete mapping with userIdType EXTERNAL and check that it is deleted
            assertTrue(UserIdMapping.deleteUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL, false));

            // check that mapping does not exist
            assertNull(UserIdMapping.getUserIdMapping(process.main, externalUserId, UserIdType.EXTERNAL));

        }

        {
            {
                // create mapping and check that it exists
                UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo,
                        false);

                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

                assertNotNull(response);
                assertEquals(userIdMapping_1, response);

                // delete mapping with superTokensUserId with userIdType ANY and check that it is deleted
                assertTrue(UserIdMapping.deleteUserIdMapping(process.main, superTokensUserId, UserIdType.ANY, false));

                // check that mapping does not exist
                assertNull(UserIdMapping.getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS));
            }

            {
                // create mapping and check that it exists
                UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo,
                        false);

                io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                        .getUserIdMapping(process.main, externalUserId, UserIdType.ANY);

                assertNotNull(response);
                assertEquals(userIdMapping_1, response);

                // delete mapping with externalUserId with userIdType ANY and check that it is deleted
                assertTrue(UserIdMapping.deleteUserIdMapping(process.main, externalUserId, UserIdType.ANY, false));

                // check that mapping does not exist
                assertNull(UserIdMapping.getUserIdMapping(process.main, externalUserId, UserIdType.ANY));
            }
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testDeletingUserIdMappingWithSharedId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // Create two UserId mappings where superTokensUserId in Mapping 1 = externalUserId in mapping 2

        // Create UserId mapping 1

        UserInfo userInfo_1 = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_1 = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo_1.id, "externalUserId", "externalUserIdInfo");

        // create the mapping and check that it exists
        {
            UserIdMapping.createUserIdMapping(process.main, userIdMapping_1.superTokensUserId,
                    userIdMapping_1.externalUserId, userIdMapping_1.externalUserIdInfo, false);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, userIdMapping_1.superTokensUserId, UserIdType.SUPERTOKENS);
            assertEquals(userIdMapping_1, response);
        }

        // Create UserId mapping 2

        UserInfo userInfo_2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_2 = new io.supertokens.pluginInterface.useridmapping.UserIdMapping(
                userInfo_2.id, userIdMapping_1.superTokensUserId, "externalUserIdInfo2");

        // create the mapping and check that it exists
        {
            UserIdMapping.createUserIdMapping(process.main, userIdMapping_2.superTokensUserId,
                    userIdMapping_2.externalUserId, userIdMapping_2.externalUserIdInfo, true);
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, userIdMapping_2.superTokensUserId, UserIdType.SUPERTOKENS);
            assertEquals(userIdMapping_2, response);
        }

        // delete userIdMapping with userIdMapping_2.externalUserId with userIdType ANY, userIdMapping_1 should be
        // deleted
        assertTrue(
                UserIdMapping.deleteUserIdMapping(process.main, userIdMapping_2.externalUserId, UserIdType.ANY, false));

        assertNull(UserIdMapping.getUserIdMapping(process.main, userIdMapping_1.superTokensUserId,
                UserIdType.SUPERTOKENS));

        // check that userIdMapping_2 still exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping response = UserIdMapping
                    .getUserIdMapping(process.main, userIdMapping_2.superTokensUserId, UserIdType.SUPERTOKENS);
            assertEquals(userIdMapping_2, response);
        }

        // delete userIdMapping with userIdMapping_2.externalUserId with EXTERNAL, userIdMapping_2 should be deleted
        assertTrue(UserIdMapping.deleteUserIdMapping(process.main, userIdMapping_2.externalUserId, UserIdType.EXTERNAL,
                false));
        assertNull(UserIdMapping.getUserIdMapping(process.main, userIdMapping_2.superTokensUserId,
                UserIdType.SUPERTOKENS));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithUnknownUserId() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String userId = "unknownId";

        // update with unknown supertokensUserId
        assertFalse(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, userId, UserIdType.SUPERTOKENS, null));

        // update with unknown externalUserId
        assertFalse(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, userId, UserIdType.EXTERNAL, null));

        // update with unknown userId
        assertFalse(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, userId, UserIdType.ANY, null));

        // check that there are no mappings with the userId

        assertNull(UserIdMapping.getUserIdMapping(process.main, userId, UserIdType.ANY));

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfo() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create User
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";

        // create a userId mapping
        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        // update from null to externalUserIdInfo using userIdType SUPERTOKENS
        String externalUserIdInfo = "externalUserIdInfo";
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, superTokensUserId,
                UserIdType.SUPERTOKENS, externalUserIdInfo));

        // retrieve mapping and validate
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // update externalUserIdInfo using userIdType EXTERNAL
        String newExternalUserIdInfo = "newExternalUserIdInfo";
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId, UserIdType.EXTERNAL,
                newExternalUserIdInfo));

        // retrieve mapping and validate with the new externalUserIdInfo
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(newExternalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // delete externalUserIdInfo by passing null with superTokensUserId with ANY
        assertTrue(
                UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, superTokensUserId, UserIdType.ANY, null));

        // retrieve mapping and check that externalUserIdInfo is null
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertNull(userIdMapping.externalUserIdInfo);
        }

        // update the externalUserIdInfo with externalUserId with ANY
        {
            assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId, UserIdType.ANY,
                    externalUserIdInfo));

            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingExternalUserIdInfoWithSharedUserIds() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create two UserMappings where superTokensUserId in Mapping 1 = externalUserId in Mapping 2

        // Create mapping 1
        UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        String superTokensUserId = userInfo.id;
        String externalUserId = "externalId";
        String externalUserIdInfo = "externalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo, false);

        // check that mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId, userIdMapping.superTokensUserId);
            assertEquals(externalUserId, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo, userIdMapping.externalUserIdInfo);
        }

        // Create mapping 2
        UserInfo userInfo2 = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
        String superTokensUserId2 = userInfo2.id;
        String externalUserId2 = userInfo.id;
        String externalUserIdInfo2 = "newExternalUserIdInfo";

        UserIdMapping.createUserIdMapping(process.main, superTokensUserId2, externalUserId2, externalUserIdInfo2, true);

        // check that the mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId2, UserIdType.SUPERTOKENS);
            assertNotNull(userIdMapping);
            assertEquals(superTokensUserId2, userIdMapping.superTokensUserId);
            assertEquals(externalUserId2, userIdMapping.externalUserId);
            assertEquals(externalUserIdInfo2, userIdMapping.externalUserIdInfo);
        }

        // update the mapping with externalUserId2 with userIdType ANY, userId mapping 1 should be updated
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId2, UserIdType.ANY, null));

        // check that userId mapping 1 got updated and userId mapping 2 is the same
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_1 = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId, UserIdType.SUPERTOKENS);
            assertNotNull(userIdMapping_1);
            assertEquals(superTokensUserId, userIdMapping_1.superTokensUserId);
            assertEquals(externalUserId, userIdMapping_1.externalUserId);
            assertNull(userIdMapping_1.externalUserIdInfo);

            io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping_2 = UserIdMapping
                    .getUserIdMapping(process.main, superTokensUserId2, UserIdType.SUPERTOKENS);

            assertNotNull(userIdMapping_2);
            assertEquals(superTokensUserId2, userIdMapping_2.superTokensUserId);
            assertEquals(externalUserId2, userIdMapping_2.externalUserId);
            assertEquals(externalUserIdInfo2, userIdMapping_2.externalUserIdInfo);
        }

        // delete externalUserIdInfo with EXTERNAL from userIdMapping 2 and check that it gets updated
        assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, externalUserId2, UserIdType.EXTERNAL,
                null));

        io.supertokens.pluginInterface.useridmapping.UserIdMapping userIdMapping = UserIdMapping
                .getUserIdMapping(process.main, superTokensUserId2, UserIdType.SUPERTOKENS);

        assertNotNull(userIdMapping);
        assertEquals(superTokensUserId2, userIdMapping.superTokensUserId);
        assertEquals(externalUserId2, userIdMapping.externalUserId);
        assertNull(userIdMapping.externalUserIdInfo);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatingTheExternalUserIdInfoOfAMappingWithTheSameValue() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create a userIdMapping with externalUserIdInfo as null and update it to null
        {
            UserInfo userInfo = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
            String superTokensUserId = userInfo.id;
            String externalUserId = "externalUserId";

            // create mapping
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, null, false);

            // update the externalUserIdInfo to the same value
            assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, superTokensUserId,
                    UserIdType.SUPERTOKENS, null));
        }

        {
            UserInfo userInfo = EmailPassword.signUp(process.main, "test2@example.com", "testPass123");
            String superTokensUserId = userInfo.id;
            String externalUserId = "newExternalUserIdInfo";
            String externalUserIdInfo = "externalUserIdInfo";

            // create mapping
            UserIdMapping.createUserIdMapping(process.main, superTokensUserId, externalUserId, externalUserIdInfo,
                    false);

            // update the externalUserIdInfo to the same value
            assertTrue(UserIdMapping.updateOrDeleteExternalUserIdInfo(process.main, superTokensUserId,
                    UserIdType.SUPERTOKENS, externalUserIdInfo));
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkThatCreateUserIdMappingHasAllNonAuthRecipeChecks() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }
        // this list contains the package names for recipes which dont use UserIdMapping
        ArrayList<String> nonAuthRecipesWhichDontNeedUserIdMapping = new ArrayList<>(
                List.of("io.supertokens.pluginInterface.jwt.JWTRecipeStorage"));

        Reflections reflections = new Reflections("io.supertokens.pluginInterface");
        Set<Class<? extends NonAuthRecipeStorage>> classes = reflections.getSubTypesOf(NonAuthRecipeStorage.class);
        List<String> names = classes.stream().map(Class::getCanonicalName).collect(Collectors.toList());
        List<String> classNames = new ArrayList<>();
        for (String name : names) {
            if (!name.contains("SQLStorage")) {
                classNames.add(name);
            }
        }

        String userId = "testUserId";
        for (String className : classNames) {
            // create entry in nonAuth table
            StorageLayer.getStorage(process.main).addInfoToNonAuthRecipesBasedOnUserId(className, userId);
            // try to create the mapping with superTokensId
            String errorMessage = null;
            try {
                UserIdMapping.createUserIdMapping(process.main, userId, "externalId", null, false);
            } catch (ServletException e) {
                errorMessage = e.getRootCause().getMessage();
            } catch (UnknownSuperTokensUserIdException e) {
                if (nonAuthRecipesWhichDontNeedUserIdMapping.contains(className)) {
                    // ignore the error
                } else {
                    throw e;
                }
            }
            // we ignore results when using a class name from the nonAuthRecipesWhichDontNeedUserIdMapping list
            if (!nonAuthRecipesWhichDontNeedUserIdMapping.contains(className)) {
                assertNotNull(errorMessage);
                assertTrue(errorMessage.contains("UserId is already in use"));
            }

            // delete user data
            AuthRecipe.deleteUser(process.main, userId);
        }
        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkThatAddInfoToNonAuthRecipesBasedOnUserIdThrowsAnErrorWithUnknownRecipe() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        Exception error = null;
        try {
            StorageLayer.getStorage(process.main).addInfoToNonAuthRecipesBasedOnUserId("unknownRecipe", "testUserId");
        } catch (IllegalStateException e) {
            error = e;
        }

        assertNotNull(error);
        assertEquals("ClassName: unknownRecipe is not part of NonAuthRecipeStorage", error.getMessage());

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void checkThatDeleteUserIdMappingHasAllNonAuthRecipeChecks() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        ArrayList<String> nonAuthRecipesWhichDontNeedUserIdMapping = new ArrayList<>(
                List.of("io.supertokens.pluginInterface.jwt.JWTRecipeStorage"));
        Reflections reflections = new Reflections("io.supertokens.pluginInterface");
        Set<Class<? extends NonAuthRecipeStorage>> classes = reflections.getSubTypesOf(NonAuthRecipeStorage.class);
        List<String> names = classes.stream().map(Class::getCanonicalName).collect(Collectors.toList());
        List<String> classNames = new ArrayList<>();
        for (String name : names) {
            if (!name.contains("SQLStorage")) {
                classNames.add(name);
            }
        }
        String externalId = "externalId";
        for (String className : classNames) {
            // Create a User
            UserInfo user = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

            // create a mapping with the user
            UserIdMapping.createUserIdMapping(process.main, user.id, externalId, null, false);

            // create entry in nonAuth table with externalId
            StorageLayer.getStorage(process.main).addInfoToNonAuthRecipesBasedOnUserId(className, externalId);

            // try to delete UserIdMapping
            String errorMessage = null;
            try {
                UserIdMapping.deleteUserIdMapping(process.main, user.id, UserIdType.SUPERTOKENS, false);
            } catch (ServletException e) {
                errorMessage = e.getRootCause().getMessage();
            }
            if (!nonAuthRecipesWhichDontNeedUserIdMapping.contains(className)) {
                assertNotNull(errorMessage);
                assertTrue(errorMessage.contains("UserId is already in use"));
            }
            // delete user data
            AuthRecipe.deleteUser(process.main, user.id);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // in https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit#gid=0
    // check that we dont allow state A5 to be created when force is false
    @Test
    public void checkThatWeDontAllowDBStateA5FromBeingCreatedWhenForceIsFalse() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create an EmailPassword User
        UserInfo user_1 = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        // create a mapping for the EmailPassword User
        UserIdMapping.createUserIdMapping(process.main, user_1.id, "externalId", null, false);

        // create some metadata for the user
        JsonObject data = new JsonObject();
        data.addProperty("test", "testData");
        UserMetadata.updateUserMetadata(process.main, "externalId", data);

        // Create another User
        UserInfo user_2 = EmailPassword.signUp(process.main, "test123@example.com", "testPass123");

        // try and map user_2 to user_1s superTokensUserId
        String errorMessage = null;
        try {
            UserIdMapping.createUserIdMapping(process.main, user_2.id, user_1.id, null, false);
        } catch (ServletException e) {
            errorMessage = e.getRootCause().getMessage();
        }
        assertNotNull(errorMessage);
        assertEquals("Cannot create a userId mapping where the externalId is also a SuperTokens userID", errorMessage);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testThatWeDontAllowDBStateA6WithoutForce() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create user 1
        UserInfo user_1 = EmailPassword.signUp(process.main, "test@example.com", "testPass123");

        // create user 2
        UserInfo user_2 = EmailPassword.signUp(process.main, "test123@example.com", "testPass123");

        // create a mapping between User_1 and User_2 with force
        UserIdMapping.createUserIdMapping(process.main, user_1.id, user_2.id, null, true);

        // try and create a mapping between User_2 and User_1 without force
        String errorMessage = null;
        try {
            UserIdMapping.createUserIdMapping(process.main, user_2.id, user_1.id, null, false);
        } catch (ServletException e) {
            errorMessage = e.getRootCause().getMessage();
        }
        assertNotNull(errorMessage);
        assertEquals("Cannot create a userId mapping where the externalId is also a SuperTokens userID", errorMessage);

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // create User_1 and User_2
    // Map User_2 to User_1 with force
    // try deleting mapping with User_1s id set as ANY(does not require force)
    // should delete the mapping

    @Test
    public void testDeleteMappingWithUser_1AndUserIdTypeAsAny() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create User_1 and User_2
        UserInfo user_1 = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserInfo user_2 = EmailPassword.signUp(process.main, "test123@exmaple.com", "testPass123");

        // create a mapping between User_2 and User_1 with force
        UserIdMapping.createUserIdMapping(process.main, user_2.id, user_1.id, null, true);

        // check that mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = UserIdMapping
                    .getUserIdMapping(process.main, user_2.id, UserIdType.SUPERTOKENS);
            assertNotNull(mapping);
            assertEquals(mapping.superTokensUserId, user_2.id);
            assertEquals(mapping.externalUserId, user_1.id);
        }

        // delete mapping with User_1s Id and UserIdType set to ANY, it should delete the mapping
        assertTrue(UserIdMapping.deleteUserIdMapping(process.main, user_1.id, UserIdType.ANY, false));

        // check that mapping is deleted
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = UserIdMapping
                    .getUserIdMapping(process.main, user_2.id, UserIdType.SUPERTOKENS);
            assertNull(mapping);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    // create User_1 and User_2
    // Map User_2 to User_1 with force
    // try deleting mapping with User_1s id set as SUPERTOKENS(does not require force)
    // should delete the mapping
    @Test
    public void testDeleteMappingWithUser_1AndUserIdTypeAsSUPERTOKENS() throws Exception {
        String[] args = { "../" };
        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        // create User_1 and User_2
        UserInfo user_1 = EmailPassword.signUp(process.main, "test@example.com", "testPass123");
        UserInfo user_2 = EmailPassword.signUp(process.main, "test123@exmaple.com", "testPass123");

        // create a mapping between User_2 and User_1 with force
        UserIdMapping.createUserIdMapping(process.main, user_2.id, user_1.id, null, true);

        // check that mapping exists
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = UserIdMapping
                    .getUserIdMapping(process.main, user_2.id, UserIdType.SUPERTOKENS);
            assertNotNull(mapping);
            assertEquals(mapping.superTokensUserId, user_2.id);
            assertEquals(mapping.externalUserId, user_1.id);
        }

        // delete mapping with User_1s Id and UserIdType set to ANY, it should delete the mapping
        assertTrue(UserIdMapping.deleteUserIdMapping(process.main, user_1.id, UserIdType.SUPERTOKENS, false));

        // check that mapping is deleted
        {
            io.supertokens.pluginInterface.useridmapping.UserIdMapping mapping = UserIdMapping
                    .getUserIdMapping(process.main, user_2.id, UserIdType.SUPERTOKENS);
            assertNull(mapping);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
