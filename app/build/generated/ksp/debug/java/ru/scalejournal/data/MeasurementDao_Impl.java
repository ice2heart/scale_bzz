package ru.scalejournal.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MeasurementDao_Impl implements MeasurementDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<Measurement> __insertionAdapterOfMeasurement;

  private final EntityDeletionOrUpdateAdapter<Measurement> __deletionAdapterOfMeasurement;

  public MeasurementDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMeasurement = new EntityInsertionAdapter<Measurement>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `measurements` (`id`,`timeMillis`,`weightKg`,`impedanceOhm`,`source`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Measurement entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimeMillis());
        statement.bindDouble(3, entity.getWeightKg());
        if (entity.getImpedanceOhm() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getImpedanceOhm());
        }
        statement.bindString(5, entity.getSource());
      }
    };
    this.__deletionAdapterOfMeasurement = new EntityDeletionOrUpdateAdapter<Measurement>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `measurements` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final Measurement entity) {
        statement.bindLong(1, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final Measurement m, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMeasurement.insertAndReturnId(m);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final Measurement m, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfMeasurement.handle(m);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<Measurement>> observeAll() {
    final String _sql = "SELECT * FROM measurements ORDER BY timeMillis DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"measurements"}, new Callable<List<Measurement>>() {
      @Override
      @NonNull
      public List<Measurement> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "timeMillis");
          final int _cursorIndexOfWeightKg = CursorUtil.getColumnIndexOrThrow(_cursor, "weightKg");
          final int _cursorIndexOfImpedanceOhm = CursorUtil.getColumnIndexOrThrow(_cursor, "impedanceOhm");
          final int _cursorIndexOfSource = CursorUtil.getColumnIndexOrThrow(_cursor, "source");
          final List<Measurement> _result = new ArrayList<Measurement>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final Measurement _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimeMillis;
            _tmpTimeMillis = _cursor.getLong(_cursorIndexOfTimeMillis);
            final double _tmpWeightKg;
            _tmpWeightKg = _cursor.getDouble(_cursorIndexOfWeightKg);
            final Double _tmpImpedanceOhm;
            if (_cursor.isNull(_cursorIndexOfImpedanceOhm)) {
              _tmpImpedanceOhm = null;
            } else {
              _tmpImpedanceOhm = _cursor.getDouble(_cursorIndexOfImpedanceOhm);
            }
            final String _tmpSource;
            _tmpSource = _cursor.getString(_cursorIndexOfSource);
            _item = new Measurement(_tmpId,_tmpTimeMillis,_tmpWeightKg,_tmpImpedanceOhm,_tmpSource);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
