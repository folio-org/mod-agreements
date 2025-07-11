package com.k_int.folio;

import java.io.IOException;

@FunctionalInterface
public interface FolioCall<T> {
  // These are the exceptions that can be thrown by FolioClient
  T execute() throws FolioClientException, InterruptedException, IOException;
}
