package io.blockchainetl.anomaloustransactions.service;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;

import static io.blockchainetl.anomaloustransactions.Constants.ONE_ETHER_IN_WEI;

public class BigQueryServiceImpl implements BigQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(BigQueryServiceImpl.class);
    
    private static final BigInteger DEFAULT_ETHER_VALUE_THRESHOLD = new BigInteger("1000").multiply(ONE_ETHER_IN_WEI);
    private static final BigInteger DEFAULT_GAS_PRICE_THRESHOLD = new BigInteger("20000000000000");

    @Override
    public BigInteger getEtherValueThreshold(Integer numberOfTransactionsAboveThreshold, Integer periodInDays) {
        String query = String.format("select value\n"
            + "from `bigquery-public-data.crypto_ethereum.transactions` as t\n"
            + "where DATE(block_timestamp) > DATE_ADD(CURRENT_DATE() , INTERVAL -%s DAY)\n"
            + "order by value desc\n"
            + "limit %s", periodInDays, numberOfTransactionsAboveThreshold);

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();

        BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
        TableResult tableResult;
        try {
            LOG.info("Calling BigQuery: " + query);
            tableResult = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        LOG.info("Got results with size: " + tableResult.getTotalRows());

        BigInteger result = getLastValueFromTableResult(tableResult, "value");

        if (result == null) {
            LOG.info("No rows in BigQuery results. Using default value.");
            result = DEFAULT_ETHER_VALUE_THRESHOLD;
        }

        LOG.info("Ether value threshold is: " + result.toString());
        
        return result;
    }

    @Override
    public BigInteger getGasPriceThreshold(Integer numberOfTransactionsAboveThreshold, Integer periodInDays) {
        String query = String.format("select gas_price\n"
            + "from `bigquery-public-data.crypto_ethereum.transactions` as t\n"
            + "where DATE(block_timestamp) > DATE_ADD(CURRENT_DATE() , INTERVAL -%s DAY)\n"
            + "order by gas_price desc\n"
            + "limit %s", periodInDays, numberOfTransactionsAboveThreshold);

        QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(query).build();

        BigQuery bigquery = BigQueryOptions.getDefaultInstance().getService();
        TableResult tableResult;
        try {
            LOG.info("Calling BigQuery: " + query);
            tableResult = bigquery.query(queryConfig);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        LOG.info("Got results with size: " + tableResult.getTotalRows());

        BigInteger result = getLastValueFromTableResult(tableResult, "gas_price");

        if (result == null) {
            LOG.info("No rows in BigQuery results. Using default value.");
            result = DEFAULT_GAS_PRICE_THRESHOLD;
        }

        LOG.info("Gas price threshold is: " + result.toString());

        return result;
    }

    private BigInteger getLastValueFromTableResult(TableResult tableResult, String fieldName) {
        BigInteger result = null;
        for (FieldValueList row : tableResult.iterateAll()) {
            FieldValue value = row.get(fieldName);

            if (value == null || value.getNumericValue() == null) {
                throw new IllegalArgumentException("Value is null in table result.");
            }

            BigDecimal numericValue = value.getNumericValue();

            result = numericValue.toBigInteger();
        }
        return result;
    }
}
