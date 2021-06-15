package com.reactnative.googlefit;

import android.os.AsyncTask;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.HealthDataTypes;
import com.google.android.gms.fitness.request.DataDeleteRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;

import java.util.List;
import java.util.concurrent.TimeUnit;


import static com.google.android.gms.fitness.data.HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL;



public class BloodGlucoseHistory {

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;
    private DataSet Dataset;
    private final DataType dataType = HealthDataTypes.TYPE_BLOOD_GLUCOSE;

    private static final String TAG = "Blood Glucose History";

    public BloodGlucoseHistory(ReactContext reactContext, GoogleFitManager googleFitManager) {
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
    }


    public ReadableArray getHistory(long startTime, long endTime, int bucketInterval, String bucketUnit) {

        DataReadRequest.Builder readRequestBuilder = new DataReadRequest.Builder()
                .read(this.dataType)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS);
        readRequestBuilder.bucketByTime(bucketInterval, HelperUtil.processBucketUnit(bucketUnit));

        DataReadRequest readRequest = readRequestBuilder.build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);

        WritableArray map = Arguments.createArray();

        //Used for aggregated data
        if (dataReadResult.getBuckets().size() > 0) {
            for (Bucket bucket : dataReadResult.getBuckets()) {

                List<DataSet> dataSets = bucket.getDataSets();
                for (DataSet dataSet : dataSets) {
                    processDataSet(dataSet, map);
                }
            }
        }
        //Used for non-aggregated data
        else if (dataReadResult.getDataSets().size() > 0) {
            for (DataSet dataSet : dataReadResult.getDataSets()) {
                processDataSet(dataSet, map);
            }
        }
        Log.d(TAG, map.toString());
        return map;
    }


    public boolean save(ReadableMap sample) {
        this.Dataset = createDataForRequest(
                this.dataType,
                DataSource.TYPE_RAW,
                (float) sample.getDouble("value"),
                (long)sample.getDouble("date"),
                (long)sample.getDouble("date"),
                TimeUnit.MILLISECONDS
        );
        new InsertAndVerifyDataTask(this.Dataset).execute();
        return true;
    }

    public boolean delete(ReadableMap sample) {
        long endTime = (long) sample.getDouble("endTime");
        long startTime = (long) sample.getDouble("startTime");
        new DeleteDataTask(startTime, endTime, this.dataType).execute();
        return true;
    }

    //Async fit data delete
    private class DeleteDataTask extends AsyncTask<Void, Void, Void> {

        long startTime;
        long endTime;
        DataType dataType;

        DeleteDataTask(long startTime, long endTime, DataType dataType) {
            this.startTime = startTime;
            this.endTime = endTime;
        }

        @Override
        protected Void doInBackground(Void... params) {

            DataDeleteRequest request = new DataDeleteRequest.Builder()
                    .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                    .addDataType(this.dataType)
                    .build();

            com.google.android.gms.common.api.Status insertStatus =
                    Fitness.HistoryApi.deleteData(googleFitManager.getGoogleApiClient(), request)
                            .await(1, TimeUnit.MINUTES);

            if (insertStatus.isSuccess()) {
                Log.d("myLog", "+Successfully deleted data.");
            } else {
                Log.d("myLog", "+Failed to delete data.");
            }

            return null;
        }
    }

    private class InsertAndVerifyDataTask extends AsyncTask<Void, Void, Void> {

        private DataSet Dataset;

        InsertAndVerifyDataTask(DataSet dataset) {
            this.Dataset = dataset;
        }

        protected Void doInBackground(Void... params) {

            DataSet dataSet = this.Dataset;
            Log.d("dataSet",dataSet.toString());
            com.google.android.gms.common.api.Status insertStatus =
                    Fitness.HistoryApi.insertData(googleFitManager.getGoogleApiClient(), dataSet)
                            .await(1, TimeUnit.MINUTES);
            Log.d("insertStatus",insertStatus.toString());
            if (!insertStatus.isSuccess()) {
                Log.d(TAG, "There was a problem inserting the dataset.");
                return null;
            }
            Log.d(TAG, "Data insert was successful!");
            return null;
        }
    }

    private DataSet createDataForRequest(DataType dataType, int dataSourceType, float value, long startTime, long endTime, TimeUnit timeUnit) {

        DataSource dataSource = new DataSource.Builder()
                .setAppPackageName(GoogleFitPackage.PACKAGE_NAME)
                .setDataType(dataType)
                .setType(dataSourceType)
                .build();


        DataSet dataSet = DataSet.create(dataSource);
        DataPoint bloodGlucose = dataSet.createDataPoint().setTimeInterval(startTime, endTime, timeUnit);


        bloodGlucose.getValue(FIELD_BLOOD_GLUCOSE_LEVEL).setFloat(value);

        dataSet.add(bloodGlucose);

        return dataSet;
    }

    private void processDataSet(DataSet dataSet, WritableArray map) {
        for (DataPoint dp : dataSet.getDataPoints()) {
            WritableMap stepMap = Arguments.createMap();
            stepMap.putDouble("startDate", dp.getStartTime(TimeUnit.MILLISECONDS));
            stepMap.putDouble("endDate", dp.getEndTime(TimeUnit.MILLISECONDS));
            stepMap.putString("addedBy", dp.getOriginalDataSource().getAppPackageName());
            stepMap.putDouble("value", dp.getValue(FIELD_BLOOD_GLUCOSE_LEVEL).asFloat());

            map.pushMap(stepMap);
        }
    }

}