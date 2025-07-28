import pandas as pd
import numpy as np
from sklearn.preprocessing import MinMaxScaler, LabelEncoder
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, mean_absolute_error
import tensorflow as tf
from tensorflow.keras.models import Sequential
from tensorflow.keras.layers import LSTM, Dense, Dropout, Input
from tensorflow.keras.optimizers import Adam
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau
import matplotlib.pyplot as plt
import warnings
warnings.filterwarnings('ignore')

class VolcanoLSTMForecaster:
    def __init__(self, sequence_length=30, forecast_days=7):
        self.sequence_length = sequence_length
        self.forecast_days = forecast_days
        self.scalers = {}
        self.label_encoders = {}
        self.model = None
        self.feature_columns = None
        self.target_columns = None
        
    def load_and_prepare_data(self, csv_path):
        """Load and prepare volcano data for LSTM training"""
        print("Loading volcano activity data...")
        df = pd.read_csv(csv_path)
        
        # Convert date and sort chronologically (oldest first for time series)
        df['Date'] = pd.to_datetime(df['Date'])
        df = df.sort_values('Date').reset_index(drop=True)
        
        # Define key target variables for forecasting
        self.target_columns = [
            'Alert_Level', 'Eruption_Count', 'Eruption_Severity_Score',
            'Volcanic_Earthquakes', 'Volcanic_Tremors', 'SO2_Flux_tpd',
            'Plume_Height_m', 'Plume_Strength'
        ]
        
        # Encode categorical features
        categorical_cols = ['Plume_Drift_Direction']
        for col in categorical_cols:
            le = LabelEncoder()
            df[col] = le.fit_transform(df[col].astype(str))
            self.label_encoders[col] = le
        
        # Select numeric features for modeling
        numeric_cols = [
            'Alert_Level', 'Acidity_pH', 'Crater_Temperature_C', 'SO2_Flux_tpd',
            'Plume_Height_m', 'Plume_Drift_Direction', 'Plume_Strength',
            'Eruption_Count', 'Eruption_Severity_Score',
            'Total_Eruption_Duration_Min', 'Avg_Eruption_Duration_Min',
            'Volcanic_Earthquakes', 'Volcanic_Tremors', 'Total_Tremor_Duration_Min',
            'Has_Long_Tremor', 'Has_Weak_Tremor', 'Caldera_Trend', 'TVI_Trend',
            'North_Trend', 'SE_Trend', 'LT_Inflation', 'LT_Deflation',
            'ST_Inflation', 'ST_Deflation'
        ]
        
        self.feature_columns = numeric_cols
        
        # Handle missing values
        df[numeric_cols] = df[numeric_cols].fillna(df[numeric_cols].median())
        
        # Scale features
        for col in numeric_cols:
            scaler = MinMaxScaler()
            df[col] = scaler.fit_transform(df[[col]])
            self.scalers[col] = scaler
        
        print(f"Prepared {len(df)} samples with {len(numeric_cols)} features")
        return df[['Date'] + numeric_cols]
    
    def create_sequences(self, data, columns):
        """Create sequences for LSTM training"""
        X, y = [], []
        
        for i in range(self.sequence_length, len(data) - self.forecast_days + 1):
            # Input sequence (past 30 days)
            X.append(data[i-self.sequence_length:i, :])  # Exclude date column
            
            # Target sequence (next 7 days for key variables)
            target_indices = [columns.get_loc(col) - 1 for col in self.target_columns]
            y.append(data[i:i+self.forecast_days, target_indices])
        
        return np.array(X), np.array(y)
    
    def build_model(self, input_shape, output_shape):
        """Build multi-output LSTM model"""
        model = Sequential([
            Input(shape=input_shape),
            LSTM(128, return_sequences=True, dropout=0.2, recurrent_dropout=0.2),
            LSTM(64, return_sequences=True, dropout=0.2, recurrent_dropout=0.2),
            LSTM(32, dropout=0.2, recurrent_dropout=0.2),
            Dense(64, activation='relu'),
            Dropout(0.3),
            Dense(32, activation='relu'),
            Dropout(0.2),
            Dense(np.prod(output_shape), activation='linear'),
            tf.keras.layers.Reshape(output_shape)
        ])
        
        model.compile(
            optimizer=Adam(learning_rate=0.001),
            loss='mse',
            metrics=['mae']
        )
        
        return model
    
    def train(self, csv_path, validation_split=0.2, epochs=100, batch_size=32):
        """Train the LSTM model"""
        # Prepare data
        df = self.load_and_prepare_data(csv_path)
        data = df.drop(columns=['Date']).values
        
        # Create sequences
        X, y = self.create_sequences(data, df.columns)
        print(f"Created {len(X)} sequences")
        print(f"Input shape: {X.shape}, Output shape: {y.shape}")
        
        # Split data
        X_train, X_val, y_train, y_val = train_test_split(
            X, y, test_size=validation_split, shuffle=False
        )
        
        # Build model
        self.model = self.build_model(
            input_shape=(X.shape[1], X.shape[2]),
            output_shape=(self.forecast_days, len(self.target_columns))
        )
        
        print("\nModel Architecture:")
        self.model.summary()
        
        # Callbacks
        callbacks = [
            EarlyStopping(patience=15, restore_best_weights=True),
            ReduceLROnPlateau(factor=0.5, patience=8, min_lr=1e-6)
        ]
        
        # Train model
        print("\nTraining model...")
        history = self.model.fit(
            X_train, y_train,
            validation_data=(X_val, y_val),
            epochs=epochs,
            batch_size=batch_size,
            callbacks=callbacks,
            verbose=1
        )
        
        # Evaluate
        train_loss = self.model.evaluate(X_train, y_train, verbose=0)
        val_loss = self.model.evaluate(X_val, y_val, verbose=0)
        
        print(f"\nTraining Loss: {train_loss[0]:.4f}")
        print(f"Validation Loss: {val_loss[0]:.4f}")
        
        # Save model
        self.model.save('volcano_lstm_model.h5')
        print("Model saved as 'volcano_lstm_model.h5'")
        
        return history
    
    def predict_next_7_days(self, csv_path, plot_results=True):
        """Predict volcano activity for next 7 days"""
        if self.model is None:
            print("Loading saved model...")
            self.model = tf.keras.models.load_model('volcano_lstm_model.h5')
        
        # Prepare recent data
        df = self.load_and_prepare_data(csv_path)
        recent_data = df.iloc[-self.sequence_length:, 1:].values  # Last 30 days
        
        assert recent_data.shape[1] == self.model.input_shape[2], \
            f"Feature mismatch: expected {self.model.input_shape[2]}, got {recent_data.shape[1]}"

        # Make prediction
        prediction = self.model.predict(recent_data.reshape(1, *recent_data.shape))
        prediction = prediction.reshape(self.forecast_days, len(self.target_columns))
        
        # Inverse transform predictions
        predictions_dict = {}
        for i, col in enumerate(self.target_columns):
            if col in self.scalers:
                pred_values = self.scalers[col].inverse_transform(
                    prediction[:, i].reshape(-1, 1)
                ).flatten()
                predictions_dict[col] = pred_values
        
        # Create forecast dataframe
        last_date = pd.to_datetime(df['Date'].iloc[-1])
        forecast_dates = pd.date_range(
            start=last_date + pd.Timedelta(days=1),
            periods=self.forecast_days,
            freq='D'
        )
        
        forecast_df = pd.DataFrame({
            'Date': forecast_dates,
            **predictions_dict
        })
        
        # Display results
        print("\n=== 7-DAY VOLCANO ACTIVITY FORECAST ===")
        print(forecast_df.round(2))
        
        # Risk assessment
        self._assess_volcanic_risk(forecast_df)
        
        if plot_results:
            self._plot_forecast(df, forecast_df)
        
        return forecast_df
    
    def _assess_volcanic_risk(self, forecast_df):
        """Assess volcanic risk based on predictions"""
        print("\n=== RISK ASSESSMENT ===")
        
        # Alert level analysis
        max_alert = forecast_df['Alert_Level'].max()
        if max_alert >= 3:
            risk_level = "HIGH"
        elif max_alert >= 2:
            risk_level = "MODERATE"
        else:
            risk_level = "LOW"
        
        print(f"Overall Risk Level: {risk_level}")
        
        # Key indicators
        max_eruptions = forecast_df['Eruption_Count'].max()
        max_earthquakes = forecast_df['Volcanic_Earthquakes'].max()
        max_so2 = forecast_df['SO2_Flux_tpd'].max()
        max_plume = forecast_df['Plume_Height_m'].max()
        
        print(f"Max Predicted Eruptions: {max_eruptions:.1f}")
        print(f"Max Volcanic Earthquakes: {max_earthquakes:.1f}")
        print(f"Max SO2 Flux: {max_so2:.0f} tonnes/day")
        print(f"Max Plume Height: {max_plume:.0f} meters")
        
        # Warnings
        warnings = []
        if max_eruptions > 1:
            warnings.append("Multiple eruptions predicted")
        if max_earthquakes > 20:
            warnings.append("High seismic activity expected")
        if max_so2 > 2000:
            warnings.append("Elevated gas emissions")
        if max_plume > 1000:
            warnings.append("Significant ash plume possible")
        
        if warnings:
            print("\nWarnings:")
            for warning in warnings:
                print(f"⚠️  {warning}")
        else:
            print("\n✅ No immediate warnings")
    
    def _plot_forecast(self, historical_df, forecast_df):
        """Plot historical data and forecast"""
        fig, axes = plt.subplots(2, 2, figsize=(15, 10))
        fig.suptitle('Taal Volcano 7-Day Activity Forecast', fontsize=16)
        
        # Plot key indicators
        indicators = [
            ('Eruption_Count', 'Eruption Count'),
            ('Volcanic_Earthquakes', 'Volcanic Earthquakes'),
            ('SO2_Flux_tpd', 'SO2 Flux (tonnes/day)'),
            ('Plume_Height_m', 'Plume Height (meters)')
        ]
        
        for idx, (col, title) in enumerate(indicators):
            ax = axes[idx // 2, idx % 2]
            
            # Historical data (last 60 days)
            hist_data = historical_df.tail(60)
            if col in self.scalers:
                hist_values = self.scalers[col].inverse_transform(
                    hist_data[col].values.reshape(-1, 1)
                ).flatten()
            else:
                hist_values = hist_data[col].values
            
            ax.plot(hist_data['Date'], hist_values, 'b-', label='Historical', alpha=0.7)
            ax.plot(forecast_df['Date'], forecast_df[col], 'r-', 
                   label='Forecast', linewidth=2, marker='o')
            
            ax.set_title(title)
            ax.set_xlabel('Date')
            ax.legend()
            ax.grid(True, alpha=0.3)
            
            # Rotate x-axis labels
            plt.setp(ax.xaxis.get_majorticklabels(), rotation=45)
        
        plt.tight_layout()
        plt.savefig('volcano_forecast.png', dpi=300, bbox_inches='tight')
        plt.show()

# Usage example
if __name__ == "__main__":
    # Initialize forecaster
    forecaster = VolcanoLSTMForecaster(sequence_length=30, forecast_days=7)
    
    # Train model
    print("Training LSTM model for volcano activity forecasting...")
    history = forecaster.train(
        csv_path='taal_cleaned_forecast_ready.csv',
        epochs=50,
        batch_size=16
    )
    
    # Make 7-day forecast
    print("\nGenerating 7-day forecast...")
    forecast = forecaster.predict_next_7_days(
        csv_path='taal_cleaned_forecast_ready.csv',
        plot_results=True
    )
    
    # Save forecast
    forecast.to_csv('volcano_7day_forecast.csv', index=False)
    print("\nForecast saved to 'volcano_7day_forecast.csv'")