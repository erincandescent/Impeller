package eu.e43.impeller.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.IOException;

import eu.e43.impeller.R;
import eu.e43.impeller.account.Authenticator;

/**
 * Created by oshepherd on 14/04/14.
 */
public class AccountPickerActivity extends ActionBarActivity implements AdapterView.OnItemClickListener, View.OnClickListener, AccountManagerCallback<Bundle> {
    static final String TAG = "AccountPickerActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_account_picker);

        AccountManager amgr = (AccountManager) getSystemService(ACCOUNT_SERVICE);

        Account[] accounts = amgr.getAccountsByType(Authenticator.ACCOUNT_TYPE);

        ListView accountList = (ListView) findViewById(R.id.account_list);
        ArrayAdapter<Account> accountAdapter = new ArrayAdapter<Account>(this, android.R.layout.simple_list_item_1,
                accounts);

        accountList.setAdapter(accountAdapter);
        accountList.setOnItemClickListener(this);

        findViewById(R.id.new_account).setOnClickListener(this);

        if(accounts.length == 0) {
            createNewAccount();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Account acct = (Account) parent.getItemAtPosition(position);

        Intent data = new Intent();
        data.putExtra(AccountManager.KEY_ACCOUNT_NAME, acct.name);
        data.putExtra(AccountManager.KEY_ACCOUNT_TYPE, acct.type);

        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.new_account:
                createNewAccount();
                break;
        }
    }

    private void createNewAccount() {
        AccountManager.get(this).addAccount(Authenticator.ACCOUNT_TYPE, null, null, null, this, this, null);
    }

    @Override
    public void run(AccountManagerFuture<Bundle> future) {
        try {
            Bundle data = future.getResult();

            Intent result = new Intent();
            result.putExtras(data);
            setResult(RESULT_OK, result);
            finish();
            return;

        } catch (OperationCanceledException e) {
            Log.v(TAG, "User cancelled");
        } catch (IOException e) {
            Log.e(TAG, "IO error", e);
        } catch (AuthenticatorException e) {
            Log.e(TAG, "Authenticator error", e);
        }

        ListView accountList = (ListView) findViewById(R.id.account_list);
        if(accountList.getAdapter().getCount() == 0) {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}
