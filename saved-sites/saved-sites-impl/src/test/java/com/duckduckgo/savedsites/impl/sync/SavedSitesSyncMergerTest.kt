/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import junit.framework.Assert.assertTrue
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SavedSitesSyncMergerTest {

    private val repository: SavedSitesRepository = mock()
    private val syncCrypto: SyncCrypto = mock()
    private val savedSitesSyncStore: FeatureSyncStore = mock()
    private lateinit var parser: SavedSitesSyncMerger

    @Before
    fun before() {
        parser = SavedSitesSyncMerger(repository, savedSitesSyncStore, syncCrypto)

        whenever(syncCrypto.decrypt(ArgumentMatchers.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
    }

    @Test
    fun whenMergingEmptyListOfChangesThenResultIsSuccess() {
        val result = parser.merge(SyncChanges.empty())

        assertTrue(result is SyncMergeResult.Success)
    }

    @Test
    fun whenMergingCorruptListOfChangesThenResultIsError() {
        val corruptedChanges = SyncChanges(BOOKMARKS, "corruptedJson")
        val result = parser.merge(corruptedChanges)

        assertTrue(result is SyncMergeResult.Error)
    }

    @Test
    fun whenMergingChangesInEmptyDBDataIsStoredSuccessfully() {
        whenever(repository.hasBookmarks()).thenReturn(false)

        val remoteChanges = SyncChanges(
            BOOKMARKS,
            "{\n" +
                "  \"bookmarks\": {\n" +
                "    \"entries\": [\n" +
                "      {\n" +
                "        \"id\": \"0212827d-b5c7-42b2-aae0-95b1bb27fc24\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"c9L6N9AN6xwCIJj+7T2spKv1k5zob22TgApYYX1QXxKyd2+iXnNHaaYLgzzunHzXUAsIR3+jbhTaUkTvjUKX8aSW\"\n" +
                "        },\n" +
                "        \"title\": \"+lekkayo44uzLxgoFTMfsjxwouU73u1oxxcseWkAG0NBk43dltYz/z+8MrmedC7gkQ==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"fd837eb1-abd6-4481-b707-757431d49e66\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"04ce3c5b-581a-45b7-ad08-f07acff636a4\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"t3NGIUyqambjXSSKL2kmAYe9u/ujMdeywz9k+JOLUsEWouUfUJqtuf/9rK2Mbb2i1JqjWSFILCnD\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"08e42664-b2c3-4223-8134-022d98eb6116\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"HyempcJAgCzOW+GDIxbGlYljQn+Ac7PP30KiFK0ZkhnoVUbg4x3GT9Eg4WYROPcd2h5KlurZ2ll0Ko6Se+CDPbpf\"\n" +
                "        },\n" +
                "        \"title\": \"SAWCOI4Bx9I35pSDF40etZxgYogSQCTTYD/LeA04WtsyPbKQ5GrinWoYu4MA8rrTY3uP1cANpkBTE6H+jys48LjudJodt6if\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"1648c9dc-33ac-466a-bae2-96d643e3075d\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"5VI1UlSgHHcfB47ZaPPw/yqcMV23Fmk0xusIKa81AuKArnBkpI6iq7eV4q0jSB72b08k23vy8RP/wMgFK490QFEV1/0Qoex90uCSA3wSwy7v\"\n" +
                "        },\n" +
                "        \"title\": \"xEqcksEIPfFWvtEMQWfrmRji2n4ZUYwiTAl7+33P3IizFBvS/CP4t3hz1G/z2eVs2ROvNhkznTJ4k9vPXkYuR3o0HAe+kGiLoTfK1cbxLtleOJWZJv60m+eXgDZ1I/q6pUtbej5Bkw37pnKn587Yqzs=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"2a45fa6a-ce4e-4d5b-bbdd-9571d2415517\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"dWZ7xGxBYkhgyZ9cO4vWiZVoJMRVDZA+aJpn6DNCndw0Y0HtjSqdi4AtTWJqXxjFweOQ6Q4xGDuRzXztFg==\"\n" +
                "        },\n" +
                "        \"title\": \"A/A/Isx491mFzWgMQruilgIwldKNLeHG5CFtI89s8PZwQYbpzMtWMDHiqbSevSxL\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"3718fe01-7a72-4f6b-83d6-00b2718a0a3a\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"3nS4EIswx6bqLNE3o6prVvdHceYYU9F50hd/g/EEQYasQQEeoAb3Ln+I+dPVfJhjV7z/gYpfPR0G4QwfHHqJZTc=\"\n" +
                "        },\n" +
                "        \"title\": \"BPrAEYHtrRG/aeIOCdsTkCrnNTiNGIOVmf7mvJiC/rUlbMa5AnC8zCMGpGK6qpxb8g==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"5da60dd1-f197-4d69-83d0-489ad6695aa7\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"Z/yvgN68LTrt46kF/cvianGeXHKXQs1l8GmjI0oIwrPxk16+yqU1fwtOWd+0cut2Vhrc9+PjGw==\"\n" +
                "        },\n" +
                "        \"title\": \"uf7UDADGrRNZlF4bUZHAZruGkW1P0B0mzA6NkDLkqMacMyXonrVmsfhb19L4H/V3CKxVpWFlEobLCVd2f6cUH5oExSs2k37Kb/2sxf5geQCCKbWP0sNqD07acPUeSez0J1OQGFc=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"2a45fa6a-ce4e-4d5b-bbdd-9571d2415517\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"64f45409-b173-475f-8fee-0a05a1ce9d6e\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"UH9YSMZ/r/kEigTKuUTOAoqyZ1hK8P703ZnOfA8ugAQMezCZXzCDx3lifEDYjT2yot148wAi\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"76130c32-58b6-447a-be52-f9d898bcdd32\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"2mrlU+Aa/2/X0+cCXm5hFt8gB7XQTesW4pajqNEoO1R9r2gaUdnKmxX0Bj0C6mwR1fqln3niOhYC/Y7auvLPMoGA\"\n" +
                "        },\n" +
                "        \"title\": \"E+uHq1P0zEwlLIbzSPqBIMzJXxB3hfJV7TqDhuMEeJPl/hMqCPq1V9BO6CGfRx85Ash9f/oVN0yIeDJcdsB2yamYOtdO56JO\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"816c8982-ab4d-4bad-ad21-ce7aae516fed\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"CvCa+VF9t1XlnIFvpHlz2WQ26140ZPP+B94QP/D5HAR0gbcBJsN/nv/1+Wi6GC9TS1GYZnG/op5d\"\n" +
                "        },\n" +
                "        \"title\": \"Nm/MbCr7l/B+l7YirBrBnY0yCrw9k1CfePLo7BISG8r5LMUdupzqiKoa5/8EbTrZYyPRsWcnQZsE0c4jZ6LZ1ByChAGWojkzkWbZq4o3Ykg1ooXjIHFpDEoE+g==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"95b6ef92-4221-41ee-9fb3-a0fdf184a9fb\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"93540e06-0402-4ef7-b657-2e8327f91f4f\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"myFRwH3U/cJOOO5ZAUfO4xybWFXLeap6QJRl52JFm7NACeTd/zIEgLJnk7EU41e2NzitouXNJUc=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"95b6ef92-4221-41ee-9fb3-a0fdf184a9fb\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"iSu8rb3R41du8A/H+LEj6838r7niy6cZWe+mpfsLUfYWyjWOU8973saU/ERStcljjS59KiMTri9/NFRtwmql\"\n" +
                "        },\n" +
                "        \"title\": \"Zr19+6WFpPRxW3s+H/y+woteZNHpA0j2Za46lzLKsn+f2RUrzte++h/e7i7XXBwxy1h+5mmZjyxVDOW98geqPdc2Z2cpMlXp3OiwTe6kTIqoDQ==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"816c8982-ab4d-4bad-ad21-ce7aae516fed\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"9fa5b01d-09d9-483e-89d5-aa6ad70a103d\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"fA/+pUYU82ALapgUfI1OtlR4UgnkhU+Y0TnyTZFqikhLInc9eYyA7fywSTQv2E1qPt3BuYYy\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"aa0399fc-3b89-4c98-ba9e-fe61477c35ea\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"9Q8lis+pvmWcqmlDtO/r21JtdLR5IXrUcXl7v6oSq95I1SDu35cTNzG9L3iYW9RK1DrSd2yzgqV1BupO7Xbgsa9+LsaDBPfTwcVXiF6nIA==\"\n" +
                "        },\n" +
                "        \"title\": \"Aqo92jo79MrqAkqlZg9rHfGPBF6alQYp9VCvPYNhbOAZ3wbUBShJGXkork9AtL2jLUd7n3pQmQacY7r6yolzxUDfUTqeWrsTXI/ZFwY2KMgiaDRgoHkUbHRqFuOpFi2GmP5odkSjOkDVb2VwULe9uuICMt2dSLuDRtxSHtsvMGK0yXNTUBDMEgjR\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"76130c32-58b6-447a-be52-f9d898bcdd32\",\n" +
                "            \"08e42664-b2c3-4223-8134-022d98eb6116\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"b669544f-c0d3-4f6a-b2af-2a025c6f4bd1\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"n0IH+Nr7sCfpJL1RzGUtCXH1hOydXfrT2kzVq/PjOJVNGZBtjyNv408ukeRYsHhwT7H4d26R1RWc94/Fzxo=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": []\n" +
                "        },\n" +
                "        \"id\": \"bcceaf71-c3d6-4b4c-a44b-414edc5c8031\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"38llaTTT1bE1vLzz++4smIR380CBriQNeDjuetfCGQUT6Jwy4jZ6R83jEsG/KWWUdQDm\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"5da60dd1-f197-4d69-83d0-489ad6695aa7\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"bookmarks_root\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"P7l+1AWJsfbpRqsWY1QqJe+LLSOLw2BYc0o9TnrlNz+07Y8dBLWYPi8jIxm9aaj+mQ==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"0212827d-b5c7-42b2-aae0-95b1bb27fc24\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"c0f3b819-585d-41fb-a099-68e8f852df60\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"h2wj4BAtgom3Kc78id5oqZ9nTQPtugKVruBSLxfRhS5oe+DffcVgnrPtyi3KyEJlKJJzGiH7\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"efc66b1b-f5d2-4041-b12d-67232f014a71\",\n" +
                "            \"de9784d7-7dd3-4f89-a7c2-9d7a86755c7e\",\n" +
                "            \"e281ca56-cd11-4100-a0e1-2a8b009eae02\",\n" +
                "            \"dac8f77d-3d37-445e-8f5e-9645abdbfea7\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"c14c94c4-f55d-40b8-9276-36043daea2ee\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"LpAGIzSO/nHzh2qegVlB4n6oPRWwmsH2SeMSWIHwMnUsD/SEEvGaJiiL3Pi+tpur4fVF6QpoVA==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"1648c9dc-33ac-466a-bae2-96d643e3075d\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"caa5dc95-081f-49c7-8cd9-8296c8def137\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"SayWNBP/ogzRThiKsiJCG8k/4ZddGtmIb+MxWrcQAOgv4bqHnC8eg0Cchh6mc9gR5ARuba8e\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"d0d2752e-f386-4576-afba-b47fad27558f\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"FKJC8nsLc4Ty9PVtu4M8Q+FdRqN13OR+45+YE4HognxwDvrLNusJcbxl/Ra9Akan2cZDzrRGPovZkbqdl3oIusF9hnraSpZ3\"\n" +
                "        },\n" +
                "        \"title\": \"M0zf7pcYafgJ15YHiKbX+D4OX+fln7VFKWp4D1jpZRWejrmWedj0qgg1+IQ2TJ6miRSsgtE=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"3718fe01-7a72-4f6b-83d6-00b2718a0a3a\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"d0eadec9-b298-42e7-a08c-c89078ae7234\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"ZOV21sNUCwSekBesj93OtxueaRNxgxSThP2HEUMoRS8w4NEg/aqWtbU3w80tsGvikVi1BqxY\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"d68bcf0a-696a-4de9-b2ba-298d027c87aa\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"1TpqJ2kL6F9EtTgawU1Bj1AqXDlhMGHHzuIpi7Mj+S/+pbnQ3IbIMD7OFVBLtvVELeXTPjlrwl5+Oc2sRZEdWGfGDw==\"\n" +
                "        },\n" +
                "        \"title\": \"cv1qDHCzH0pUexTKYDXOJplGZh9EE8Po+agzBkt3VwU2YAFYhO1Ug6+riWOuv3esczfb9+cAf1xP2fGW1IAkOEcKYegGrMJZJUOZx0qliRsg+ixiuSKFMdjYcKBH8u+A4tip69C9LYxW\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"dac8f77d-3d37-445e-8f5e-9645abdbfea7\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"WGImBUXKWly6qvKUZxyHkYb4sFygzAqJuvgpq8EHxnvO58F7Z8mBCU3BwtLs/NKehDbRv9frVqkXA/9ILaaihGkibXlYNNDSwzMW2Q==\"\n" +
                "        },\n" +
                "        \"title\": \"D4rU3yMDYkGQbswFGE3zXkYw5/X/ep4Vf7mAml1ctxR8XExQr9BXU4wnuAkJp73O\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"de9784d7-7dd3-4f89-a7c2-9d7a86755c7e\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"sOxLL0PFfcROL7kZ6iRyW1jzWNXU7hOD8xophzGdhICFzIHweTI469cDgxhfxBYc9ydUh5MWTEbPIZnBXWM8fts1y6g1CXlS6bEjVACSO5/0zmHB8icWynZXPT68ZMEXaeY3G4g5+FlYCi/BhCdWBbSov+ePC2BJg0MZjvYq0FmA23PkRD6h/Qp+AQfOfvbQRddbk1Sg0aSnlaWzHWq9V8zZe/AegEgjKd5J9eUyVv64L3SrMl5/P/wQva7m50INfNl3vlMP4LDyIoc=\"\n" +
                "        },\n" +
                "        \"title\": \"G7QZx6VjO5xKtTEbYF0S020RhXUcR9gE92L23BH6K3ZUi0LnsJ0jT7zA1UoUulO9onNb2Go5nUZq\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"e281ca56-cd11-4100-a0e1-2a8b009eae02\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"YxHIX/gGbjNK2YS/u0HHDDdNEXwhX7NarL7SFRQ3RLtaU3q/OpOltB7oT8Y5FRnrl7JrSosmu3yVOSJQ/baYFM3lBNVZFhheHAvFIoWYpSVU\"\n" +
                "        },\n" +
                "        \"title\": \"VfjW5wUKMjgd+5Rf0udw5lmVRAd2uYp+BgZNeh/xofB8xBdIKLJUTv1xjJ1bKCf8+r4xcQ==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"d68bcf0a-696a-4de9-b2ba-298d027c87aa\",\n" +
                "            \"aa0399fc-3b89-4c98-ba9e-fe61477c35ea\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"e92a8c88-59f9-4bed-9842-d7627cd12e0f\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"DrpFaUNlrklK19y50GpuT+ra9QUPInV04yjF64EUG9diRI6I5SWFKnDrHXtWqxqmDGIGJSAZc/5zCrM=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"efc66b1b-f5d2-4041-b12d-67232f014a71\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"6DEUmwjIcYEIQ6QyR0uyhFcDS1bRf91iLq+WzRts8rus+k1fNXygGyfppl18jgqWGBfZKz1nvyS0BCmSHm4dUfI3YnE4i41TgzYRd3F3DYm0eoHcp7IeltP9\"\n" +
                "        },\n" +
                "        \"title\": \"2nqxPp/Ou/TODj7UNgSbbf901DJJGyXLUQOgcLZVzdjSv8/R0Kr4Y7EnzSedPqO/\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"d0d2752e-f386-4576-afba-b47fad27558f\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"fa2c3ba4-29b9-4489-99bb-af2496acfe3d\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"YdvtFLAA+gfPFn6UwpA22JF2bFYaujZBifit9UOXbbkdEAU5jNuSAcVSfr16+RLELqfXGeUA5X0=\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"folder\": {\n" +
                "          \"children\": [\n" +
                "            \"5da60dd1-f197-4d69-83d0-489ad6695aa7\"\n" +
                "          ]\n" +
                "        },\n" +
                "        \"id\": \"favorites_root\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"title\": \"73GzDmeOGMkFza69Ador7oOE7QYR2xUl/Eif+lkmyVL4jOUKtpL/vCxeHxTyqOKxwg==\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"id\": \"fd837eb1-abd6-4481-b707-757431d49e66\",\n" +
                "        \"last_modified\": null,\n" +
                "        \"page\": {\n" +
                "          \"url\": \"iVnXlQuPYbitOjC7vmMkkhhWEBvaAalx9oCzkIjm5IKPKiYmiODllUEqRZaVti6Z268kFJA/Kr0f21DK8IueMF4s6uKd+ay2pAuVUkUFre9rzsR2iEY=\"\n" +
                "        },\n" +
                "        \"title\": \"+x5Fq3xVPD28fiwwrDmFQuCTrt9LV2Vd3eAZniepjuoFIIqshAghR1SbmX0JpitW0WlihVfHCg==\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"last_modified\": \"2023-05-02T14:28:46Z\"\n" +
                "  },\n" +
                "  \"devices\": {\n" +
                "    \"entries\": [\n" +
                "      {\n" +
                "        \"device_id\": \"eec8832f-a13a-4ce7-be05-1898dfcdef3f\",\n" +
                "        \"device_name\": \"z4iHhsP0j8gXZJ21It32NMHKdRyFry4xrG8x4EaS1DSmx1JVt2uWe5AN27TAa5b4pQzvqrsN\",\n" +
                "        \"device_type\": \"9+HjN0cVx8zr6wWdOCZXcPVLyjwQSIpZFlRKmIrdULyAGWLLBK8RLPjHs+Qm\",\n" +
                "        \"jwt_iat\": \"2023-05-02T14:28:44Z\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"device_id\": \"4959e543-8674-4257-ae9e-13a88c900dec\",\n" +
                "        \"device_name\": \"QXSCf9FDSxX4VENtT7pmunINCDstzX69a/a1kp70MLKPS/4CLMsKSSzI6koRK7YsE3W+O+anSz8=\",\n" +
                "        \"device_type\": \"LvhGmVjk1JT/qcnxdb0sju4kSJgzEmuU6wfZInwQIzyB1D7zeYY7STpJbUF6\",\n" +
                "        \"jwt_iat\": \"2023-05-02T14:32:12Z\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"last_modified\": \"\"\n" +
                "  },\n" +
                "  \"settings\": {\n" +
                "    \"entries\": [],\n" +
                "    \"last_modified\": \"1970-01-01T00:00:00Z\"\n" +
                "  }\n" +
                "}",
        )

        val result = parser.syncChanges(listOf(remoteChanges), "")
    }
}
