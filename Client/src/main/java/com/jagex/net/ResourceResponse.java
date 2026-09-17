package com.jagex.net;



import com.rspsi.cache.store.CacheCompression;

import java.io.IOException;

public class ResourceResponse {
	
	private ResourceRequest request;
	private byte[] data;
	
	public ResourceResponse(ResourceRequest request, byte[] data) {
		super();
		this.request = request;
		this.data = data;
	}

	public ResourceRequest getRequest() {
		return request;
	}

	public byte[] getData() {
		return data;
	}
	
	public byte[] decompress() {
		byte[] unzipped = CacheCompression.gunzip(data);
		return unzipped == null ? data : unzipped;
	}

}
