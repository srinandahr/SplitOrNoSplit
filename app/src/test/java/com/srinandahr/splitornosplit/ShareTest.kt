package com.srinandahr.splitornosplit

import com.srinandahr.splitornosplit.data.Bill
import com.srinandahr.splitornosplit.data.BillType
import com.srinandahr.splitornosplit.data.Member
import com.srinandahr.splitornosplit.data.Project
import com.srinandahr.splitornosplit.ui.ShareDirection
import com.srinandahr.splitornosplit.ui.billSubtitle
import com.srinandahr.splitornosplit.ui.groupByMonth
import com.srinandahr.splitornosplit.ui.shareSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "you lent / you borrowed" figure on every expense row. Worth covering directly:
 * it is the only number in the app this client computes rather than reads from the server.
 */
class ShareTest {

    private val alice = Member(id = 1, name = "Alice")
    private val bob = Member(id = 2, name = "Bob")
    private val carol = Member(id = 3, name = "Carol")

    private fun bill(
        amount: Double,
        payerId: Int,
        owers: List<Member>,
        date: String = "2026-08-01",
    ) = Bill(
        id = 1, what = "Dinner", amount = amount, payerId = payerId,
        owers = owers, date = date, currency = "INR",
    )

    @Test
    fun `splits equally between everyone on the bill`() {
        val b = bill(300.0, payerId = 1, owers = listOf(alice, bob, carol))
        assertEquals(100.0, b.shareOf(1), 0.001)
        assertEquals(100.0, b.shareOf(2), 0.001)
        assertEquals(100.0, b.shareOf(3), 0.001)
    }

    @Test
    fun `payer who is also an ower is owed the rest`() {
        val b = bill(300.0, payerId = 1, owers = listOf(alice, bob, carol))
        // Paid 300, owes 100 of it, so 200 is out of pocket.
        assertEquals(200.0, b.netFor(1), 0.001)
        assertEquals(-100.0, b.netFor(2), 0.001)
    }

    @Test
    fun `payer who is not an ower is owed the whole amount`() {
        val b = bill(300.0, payerId = 1, owers = listOf(bob, carol))
        assertEquals(300.0, b.netFor(1), 0.001)
        assertEquals(-150.0, b.netFor(2), 0.001)
    }

    @Test
    fun `someone not on the bill is unaffected`() {
        val b = bill(300.0, payerId = 1, owers = listOf(alice, bob))
        assertEquals(0.0, b.shareOf(3), 0.001)
        assertEquals(0.0, b.netFor(3), 0.001)
    }

    @Test
    fun `respects member weights for uneven splits`() {
        // I Hate Money supports weights; a couple counted as 2 shares should owe double.
        val heavy = carol.copy(weight = 2.0)
        val b = bill(400.0, payerId = 1, owers = listOf(alice, bob, heavy))
        assertEquals(100.0, b.shareOf(1), 0.001)
        assertEquals(100.0, b.shareOf(2), 0.001)
        assertEquals(200.0, b.shareOf(3), 0.001)
    }

    @Test
    fun `does not divide by zero when weights are degenerate`() {
        val zero = alice.copy(weight = 0.0)
        val b = bill(100.0, payerId = 1, owers = listOf(zero))
        assertEquals(0.0, b.shareOf(1), 0.001)
    }

    @Test
    fun `labels the row from the viewer's perspective`() {
        val b = bill(300.0, payerId = 1, owers = listOf(alice, bob, carol))

        val asPayer = shareSummary(b, myMemberId = 1)
        assertEquals(ShareDirection.LENT, asPayer.direction)
        assertEquals("you lent", asPayer.label)
        assertEquals(200.0, asPayer.amount, 0.001)

        val asOwer = shareSummary(b, myMemberId = 2)
        assertEquals(ShareDirection.BORROWED, asOwer.direction)
        assertEquals("you borrowed", asOwer.label)
        assertEquals(100.0, asOwer.amount, 0.001)

        val bystander = shareSummary(b, myMemberId = 99)
        assertEquals(ShareDirection.NEUTRAL, bystander.direction)
    }

    @Test
    fun `treats an unset member as neutral rather than crashing`() {
        val b = bill(300.0, payerId = 1, owers = listOf(alice, bob))
        assertEquals(ShareDirection.NEUTRAL, shareSummary(b, myMemberId = null).direction)
    }

    @Test
    fun `a settlement moves money without creating a new debt`() {
        // Bob hands Alice 125 to pay down what he owes: his balance rises, hers falls.
        val payment = bill(125.0, payerId = 2, owers = listOf(alice))
            .copy(billType = BillType.REIMBURSEMENT, what = "Bob paid Alice")

        assertEquals(125.0, payment.netFor(2), 0.001)
        assertEquals(-125.0, payment.netFor(1), 0.001)
        assertEquals(0.0, payment.netFor(3), 0.001)
    }

    @Test
    fun `settlement rows say paid and received rather than lent and borrowed`() {
        val payment = bill(125.0, payerId = 2, owers = listOf(alice))
            .copy(billType = BillType.REIMBURSEMENT)

        val payer = shareSummary(payment, myMemberId = 2)
        assertEquals(ShareDirection.LENT, payer.direction)
        assertEquals("you paid", payer.label)

        val recipient = shareSummary(payment, myMemberId = 1)
        assertEquals(ShareDirection.BORROWED, recipient.direction)
        assertEquals("you received", recipient.label)
    }

    @Test
    fun `settlement subtitle names both ends`() {
        val project = Project(
            instanceUrl = "https://ihatemoney.org",
            projectId = "p",
            privateCode = "c",
            name = "Flat",
            currency = "INR",
            myMemberId = 2,
            members = listOf(alice, bob),
        )
        val payment = bill(125.0, payerId = 2, owers = listOf(alice))
            .copy(billType = BillType.REIMBURSEMENT)
        assertEquals("You paid Alice ₹125.00", billSubtitle(payment, project))

        val expense = bill(300.0, payerId = 1, owers = listOf(alice, bob))
        assertEquals("Alice paid ₹300.00", billSubtitle(expense, project))
    }

    @Test
    fun `an unknown bill_type from an older server is treated as an expense`() {
        assertEquals(BillType.EXPENSE, BillType.fromWire(null))
        assertEquals(BillType.EXPENSE, BillType.fromWire("Expense"))
        assertEquals(BillType.EXPENSE, BillType.fromWire("something-new"))
        assertEquals(BillType.REIMBURSEMENT, BillType.fromWire("Reimbursement"))
        // The server validates strictly, so the wire value has to be exact.
        assertEquals("Reimbursement", BillType.REIMBURSEMENT.wire)
        assertEquals("Expense", BillType.EXPENSE.wire)
    }

    @Test
    fun `groups bills into months, newest first`() {
        val bills = listOf(
            bill(10.0, 1, listOf(alice), date = "2026-06-15").copy(id = 1),
            bill(20.0, 1, listOf(alice), date = "2026-07-31").copy(id = 2),
            bill(30.0, 1, listOf(alice), date = "2026-07-01").copy(id = 3),
        )
        val grouped = groupByMonth(bills)
        assertEquals(2, grouped.size)
        // July section comes before June, and within July the later date leads.
        assertEquals(listOf(2, 3), grouped[0].second.map { it.id })
        assertEquals(listOf(1), grouped[1].second.map { it.id })
    }
}
