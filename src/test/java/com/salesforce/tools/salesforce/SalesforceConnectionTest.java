package com.salesforce.tools.salesforce;

import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.StatusCode;
import com.sforce.soap.partner.sobject.SObject;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SalesforceConnectionTest {

    @Test
    void createSObjectSkipsNullAndEmptyValues() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("Name", "ACME");
        fields.put("AnnualRevenue", "");
        fields.put("Phone", null);

        SObject so = SalesforceConnection.createSObject("Account", fields);

        assertEquals("Account", so.getType());
        assertEquals("ACME", so.getField("Name"));
        assertNull(so.getField("AnnualRevenue"));
        assertNull(so.getField("Phone"));
    }

    @Test
    void joinErrorsIncludesStatusAndFields() {
        Error e1 = new Error();
        e1.setStatusCode(StatusCode.REQUIRED_FIELD_MISSING);
        e1.setMessage("Required fields are missing");
        e1.setFields(new String[]{"Name"});
        Error e2 = new Error();
        e2.setStatusCode(StatusCode.INVALID_FIELD);
        e2.setMessage("bad");

        String joined = SalesforceConnection.joinErrors(new Error[]{e1, e2});

        assertEquals("REQUIRED_FIELD_MISSING: Required fields are missing [Name] / INVALID_FIELD: bad", joined);
        assertEquals("", SalesforceConnection.joinErrors(null));
    }

    @Test
    void operationsRequireConnection() {
        SalesforceConnection conn = new SalesforceConnection(new SalesforceConfig());
        List<Map<String, Object>> records = List.of(Map.of("Name", "x"));
        assertThrows(IllegalStateException.class, () -> conn.insertRecords("Account", records));
        assertThrows(IllegalStateException.class, () -> conn.upsertRecords("Account", records, "Id"));
        assertDoesNotThrow(conn::disconnect);
    }
}
