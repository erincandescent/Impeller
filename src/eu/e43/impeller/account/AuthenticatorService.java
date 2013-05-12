package eu.e43.impeller.account;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AuthenticatorService extends Service {
	Authenticator m_auth;
	public AuthenticatorService() {
	}
	
	@Override
	public void onCreate()
	{
		m_auth = new Authenticator(this);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return m_auth.getIBinder();
	}
}
