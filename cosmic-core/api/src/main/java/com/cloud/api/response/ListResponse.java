package com.cloud.api.response;

import com.cloud.api.BaseResponse;
import com.cloud.api.ResponseObject;

import java.util.List;

public class ListResponse<T extends ResponseObject> extends BaseResponse {
    List<T> responses;
    private transient Integer count;

    public List<T> getResponses() {
        return responses;
    }

    public void setResponses(final List<T> responses) {
        this.responses = responses;
    }

    public void setResponses(final List<T> responses, final Integer count) {
        this.responses = responses;
        this.count = count;
    }

    public Integer getCount() {
        if (count != null) {
            return count;
        }

        if (responses != null) {
            return responses.size();
        }

        return null;
    }
}
