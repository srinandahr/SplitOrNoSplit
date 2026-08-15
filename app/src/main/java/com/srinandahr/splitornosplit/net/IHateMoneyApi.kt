package com.srinandahr.splitornosplit.net

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url

/**
 * The I Hate Money REST API.
 *
 * Every call takes an absolute @Url rather than relying on a fixed base URL, because the
 * instance is user-configurable — self-hosters must work without a rebuild, and hardcoding
 * ihatemoney.org would dump this app's traffic onto a volunteer-run server.
 *
 * The API is form-encoded, not JSON. `payed_for` is repeated once per member, which is how
 * an equal split is expressed; there is no split_equally flag as there was on Splitwise.
 */
interface IHateMoneyApi {

    /** Project creation takes no auth — the caller chooses the id and private code. */
    @FormUrlEncoded
    @POST
    suspend fun createProject(
        @Url url: String,
        @Field("name") name: String,
        @Field("id") id: String,
        @Field("password") password: String,
        @Field("contact_email") contactEmail: String,
        @Field("default_currency") defaultCurrency: String,
    ): Response<ResponseBody>

    @GET
    suspend fun getProject(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): Response<ProjectDto>

    @GET
    suspend fun getMembers(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): Response<List<MemberDto>>

    @FormUrlEncoded
    @POST
    suspend fun addMember(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Field("name") name: String,
    ): Response<ResponseBody>

    @GET
    suspend fun getBills(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): Response<List<BillDto>>

    @FormUrlEncoded
    @POST
    suspend fun createBill(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Field("what") what: String,
        @Field("payer") payer: Int,
        @Field("payed_for") payedFor: @JvmSuppressWildcards List<Int>,
        @Field("amount") amount: String,
        @Field("date") date: String,
        @Field("original_currency") originalCurrency: String,
        /** Instances predating settle-up simply ignore this field, so it is safe to always send. */
        @Field("bill_type") billType: String,
    ): Response<ResponseBody>

    /**
     * Replaces a bill wholesale — the server re-validates the whole form, so every required
     * field must be resent. Notably, omitting `bill_type` silently downgrades a Reimbursement
     * back to an Expense, which would turn a recorded settlement into a fresh debt.
     */
    @FormUrlEncoded
    @PUT
    suspend fun updateBill(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Field("what") what: String,
        @Field("payer") payer: Int,
        @Field("payed_for") payedFor: @JvmSuppressWildcards List<Int>,
        @Field("amount") amount: String,
        @Field("date") date: String,
        @Field("original_currency") originalCurrency: String,
        @Field("bill_type") billType: String,
    ): Response<ResponseBody>

    @DELETE
    suspend fun deleteBill(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): Response<ResponseBody>

    @GET
    suspend fun getStatistics(
        @Url url: String,
        @Header("Authorization") auth: String,
    ): Response<List<StatisticsDto>>
}
