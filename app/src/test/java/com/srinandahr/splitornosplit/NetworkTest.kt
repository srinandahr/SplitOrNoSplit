package com.srinandahr.splitornosplit

import com.srinandahr.splitornosplit.net.Endpoints
import com.srinandahr.splitornosplit.net.basicAuth
import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointsTest {

    @Test
    fun `builds the documented endpoint paths`() {
        val instance = "https://ihatemoney.org"
        assertEquals("https://ihatemoney.org/api/projects", Endpoints.projects(instance))
        assertEquals("https://ihatemoney.org/api/projects/demo", Endpoints.project(instance, "demo"))
        assertEquals(
            "https://ihatemoney.org/api/projects/demo/members",
            Endpoints.members(instance, "demo"),
        )
        assertEquals(
            "https://ihatemoney.org/api/projects/demo/bills",
            Endpoints.bills(instance, "demo"),
        )
        assertEquals(
            "https://ihatemoney.org/api/projects/demo/statistics",
            Endpoints.statistics(instance, "demo"),
        )
    }

    @Test
    fun `tolerates a trailing slash on a self-hosted instance`() {
        assertEquals(
            "https://money.example.com/api/projects/flat",
            Endpoints.project("https://money.example.com/", "flat"),
        )
    }

    @Test
    fun `assumes https when the user omits the scheme`() {
        assertEquals("https://money.example.com/api/projects", Endpoints.projects("money.example.com"))
    }

    @Test
    fun `keeps an explicit http scheme for local instances`() {
        assertEquals("http://192.168.1.10:8000/api/projects", Endpoints.projects("http://192.168.1.10:8000"))
    }
}

class BasicAuthTest {

    @Test
    fun `encodes credentials the way the docs describe`() {
        // curl --basic -u demo:demo  ->  ZGVtbzpkZW1v
        assertEquals("Basic ZGVtbzpkZW1v", basicAuth("demo", "demo"))
    }

    @Test
    fun `pads correctly for lengths that are not a multiple of three`() {
        assertEquals("Basic YTpi", basicAuth("a", "b"))          // "a:b"   - 3 bytes, no padding
        assertEquals("Basic YWI6Yg==", basicAuth("ab", "b"))     // "ab:b"  - 4 bytes, two pads
        assertEquals("Basic YWI6YmM=", basicAuth("ab", "bc"))    // "ab:bc" - 5 bytes, one pad
    }
}
