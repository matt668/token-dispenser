package com.github.yeriomin.tokendispenser;

import java.util.Properties;

public class PasswordsDbEnv implements PasswordsDbInterface {
  private String email;
  private String password;
  
  PasswordsDbEnv(Properties config) {
    email = System.getenv(Server.ENV_EMAIL);
    password = System.getenv(Server.ENV_PASSWORD);
    if (email == null || password == null) {
      throw new IllegalArgumentException("empty email/password, make sure to set " + Server.ENV_EMAIL + " and " + Server.ENV_PASSWORD);
    }
  }
  
  @Override
  public String getRandomEmail() {
    return email;
  }
  
  @Override
  public String get(String email) {
    if (!email.equals(this.email)) {
      throw new IllegalArgumentException("invalid email: " + email);
    }
    return password;
  }
  
  @Override
  public void put(String email, String password) {
    throw new UnsupportedOperationException("put is not supported for env storage");
  }
}
