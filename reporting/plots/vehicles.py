import helpers as h
import numpy as np
import pandas as pd
import matplotlib.pyplot as pp
import analysisConfiguration.py as cfg
import matplotlib.backends.backend_pdf

def batteryCount(df):
    return df[df.Battery > 0]['Battery'].sum()

def lastBatteryLevel(df):
    return df[df.]

def inVehicle(df, t):
    return df[(df['Pick-up Time'] <= t) & (df['Drop-off Time'] >= t)]

def activityState(df):
    if(df['Active'] >= df['Charging']):
        return 1
    else:
        return 0

def countSeats(df):
    seats = inVehicle(journeys[(journeys["Vehicle ID"] == df["Vehicle ID"])], df.name)['Seat Count'].sum()
    if seats > 0:
        return seats
    else:
        return 0

vehicles = pd.read_csv("plots/vehicle_logs.csv")
journeys = pd.read_csv('plots/journey_logs.csv')

pdf = matplotlib.backends.backend_pdf.PdfPages("plots/vehicles.pdf")

if (vehicles.size != 0) & (journeys.size != 0):
    # We analyze the data from the first booking time to the last drop-off time
    start = journeys['Booking Time'].min()
    end = journeys['Drop-off Time'].max()
    
    timestampGrouper = '10min'
    if cfg.oneHourSimu:
        start = np.datetime64('2017-01-01T00:00:00.000Z')
        end = np.datetime64('2017-01-01T01:00:00.000Z')
        cfg.frequencyForAnalysis = '1min'
        cfg.frequencyName = 'by minute'
        timestampGrouper = '1min'

    journeys = h.convertColumns(journeys, ['Booking Time','Pick-up Time','Drop-off Time'], h.todatetime)
    journeys['Waiting Time'] = (journeys['Pick-up Time'] - journeys['Booking Time']).apply(lambda x: x.seconds/60)
    journeys['Time in vehicle'] = (journeys['Drop-off Time'] - journeys['Pick-up Time']).apply(lambda x: x.seconds/60)
    timeInVehicle = journeys[['Time in vehicle']].sum().values[0]
    waitingTime =  journeys[['Waiting Time']].sum().values[0]
    waitingTimeMax = journeys['Waiting Time'].max()
    waitingTimeMin = journeys['Waiting Time'].min()
    waitingStability = waitingTimeMax-waitingTimeMin
    df_sorted = journeys.sort_values(by=['Booking Time'])
    df_hr = h.convertColumns(df_sorted, ['Booking Time','Pick-up Time','Drop-off Time'], h.tohr)

    journeys = journeys.loc[(journeys['Booking Time'] > start) & (journeys['Drop-off Time'] < end)]
    print(journeys.shape)
    vehicles = h.convertColumns(vehicles, ['TimeStamp'], h.todatetime)
    vehicles = vehicles.loc[(vehicles.TimeStamp > start) & (vehicles.TimeStamp < end)]
    vehicleNames = { id : "vehicle-" + str(index+1) for index, id in enumerate(vehicles['Vehicle ID']).unique() }
    vehicles = vehicles.replace({"Vehicle ID": vehicleNames})
    vehicles = vehicles.sort_values(by=['Vehicle ID', 'TimeStamp'])
    vehicles['Battery'] = - vehicles.groupby('Vehicle ID')['Battery Level'].diff().fillna(0)
    vehicles = vehicles.set_index("TimeStamp")
    batteryPerVehicle = vehicles.groupby('Vehicle ID')[['Battery']].agg(batteryCount)
    totalBattery = batteryPerVehicle['Battery'].sum()
    batteryPerVehicle.loc["Total"] = totalBattery
    fig1 = h.render_mpl_table(batteryPerVehicle, header_columns=0, col_width=7.0, title = "Vehicle Battery Consumption")

    time_grouper = pd.TimeGrouper(freq=cfg.frequencyForAnalysis)

    vehicles['Battery'] = vehicles['Battery'].apply(lambda x: x if (x > 0) else 0)
    battery_freq = vehicles.groupby(["Vehicle ID",time_grouper]).sum()[['Battery']]

    print(vehicles.shape)

    vehicles['Occupancy'] = vehicles.apply(countSeats, axis = 1)
    occupancy_freq = vehicles.groupby(["Vehicle ID",time_grouper]).mean()[['Occupancy']]

    vehicles['LoadFactor'] = vehicles['Occupancy']*vehicles['Battery']
    vehiclesSumPerVehicle = vehicles.groupby('Vehicle ID').sum()
    loadFactorPerVehicle = vehiclesSumPerVehicle['LoadFactor'] / (vehiclesSumPerVehicle['Battery']*cfg.maxCapacity)
    loadFactorPerVehicle = loadFactorPerVehicle.to_frame(name="Load Factor")
    averageLoadFactor = loadFactorPerVehicle['Load Factor'].mean()
    maxLoadFactor = loadFactorPerVehicle['Load Factor'].max()
    minLoadFactor = loadFactorPerVehicle['Load Factor'].min()
    stabilityLoadFactor = maxLoadFactor - minLoadFactor
    loadFactorPerVehicle.loc["Average"] = averageLoadFactor
    fig10 = h.render_mpl_table(loadFactorPerVehicle, header_columns=0, col_width=7.0, title = "Load Factor per vehicle (passenger battery/(vehicle battery*max capacity)")

    fig2, ax2 = pp.subplots()
    ax2.set_title(cfg.frequencyName + ' battery consumption per vehicle')
    for v, new_df in battery_freq.groupby(level=0):
        ax2.plot(new_df.index.get_level_values(1), new_df['Battery'].values, label=v)
    ax2.set_xlabel('Hour')
    ax2.set_ylabel('Battery (proportional to total charge)')
    ax2.xaxis_date()
    ax2.get_xaxis().set_major_formatter(matplotlib.dates.DateFormatter('%H:%M'))
    pp.xticks(rotation=70)
    pp.legend(bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)


    fig3, ax3 = pp.subplots()
    ax3.set_title('Total and average ' + cfg.frequencyName + ' battery consumption')
    battery_freq_s = vehicles.groupby(['Vehicle ID', time_grouper]).sum()
    battery_freq_s = battery_freq_s.reset_index("Vehicle ID")
    battery_freq_avg = battery_freq_s.groupby(time_grouper).mean()[['Battery']]
    battery_freq_tot = battery_freq_s.groupby(time_grouper).sum()[['Battery']]
    ax3.plot(battery_freq_avg.index, battery_freq_avg['Battery'].values, label="Average")
    ax3.plot(battery_freq_tot.index, battery_freq_tot['Battery'].values, label="Total")
    ax3.set_xlabel('Hour')
    ax3.set_ylabel('Battery (proportional to maximal charge)')
    ax3.get_xaxis().set_major_formatter(matplotlib.dates.DateFormatter('%H:%M'))
    pp.xticks(rotation=70)
    pp.legend(bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)

    fig4, ax4 = pp.subplots()
    ax4.set_title(cfg.frequencyName + ' average occupancy per vehicle')
    for v, new_df in occupancy_freq.groupby(level=0):
        ax4.plot(new_df.index.get_level_values(1), new_df['Occupancy'].values, label=v)
    ax4.set_xlabel('Hour')
    ax4.set_ylabel('Occupancy')
    ax4.get_xaxis().set_major_formatter(matplotlib.dates.DateFormatter('%H:%M'))
    pp.xticks(rotation=70)
    pp.legend(bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)

    averageTimeInVehicle = timeInVehicle /len(journeys)
    averageWaitingTime = waitingTime / len(journeys)

    fig5, ax5 = pp.subplots()
    ax5.set_title(cfg.frequencyName + ' average occupancy')
    occupancy_freq_s = vehicles.groupby(['Vehicle ID', time_grouper]).mean()
    occupancy_freq_s = occupancy_freq_s.reset_index("Vehicle ID")
    occupancy_freq_avg = occupancy_freq_s.groupby(time_grouper).mean()[['Occupancy']]
    ax5.plot(occupancy_freq_avg.index, occupancy_freq_avg['Occupancy'].values)
    ax5.set_xlabel('Hour')
    ax5.set_ylabel('Occupancy')
    ax5.get_xaxis().set_major_formatter(matplotlib.dates.DateFormatter('%H:%M'))
    pp.xticks(rotation=70)
    pp.legend(bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)

    occupancyAvg = occupancy_freq_avg.mean()['Occupancy']

    vehicles_state = vehicles
    vehicles_state['Active'] = vehicles['Battery State'].apply(lambda x: 1 if (x == 'discharging') else 0)
    vehicles_state['Charging'] = vehicles['Battery State'].apply(lambda x: 0 if (x == 'discharging') else 1)
    vehicles_state = vehicles_state.groupby(['Vehicle ID',  pd.TimeGrouper(freq=timestampGrouper)]).sum()
    vehicles_state['Activity'] = vehicles_state.apply(activityState, axis = 1)
    vehicles_active = vehicles_state[['Activity']]
    vehicles_charging = vehicles_active.apply(lambda x: 0 if x['Activity']==1 else 1, axis = 1)
    activity = vehicles_active.groupby(level=1).sum()
    charging = vehicles_charging.groupby(level=1).sum()

    nVehicles = len(set(vehicles['Vehicle ID']))

    fig8, ax8 = pp.subplots()
    ax8.set_title('Number of charging vehicles on the line and ' + cfg.frequencyName + 'average waiting time')
    ax8.plot(charging.index.tolist(),charging, label = "Number of charging vehicles")
    ax8.set_xlabel('Time')
    ax8.set_ylabel('Charging Vehicles')

    wait_hr_avg = df_hr.groupby('Booking Time').mean()
    wait_hr_avg = (wait_hr_avg - wait_hr_avg.min())*nVehicles / (wait_hr_avg.max() - wait_hr_avg.min())
    ax8.plot(wait_hr_avg.index.tolist(),wait_hr_avg['Waiting Time'], label = "normalized " + cfg.frequencyName + " average waiting time (between 0 and number of vehicles)")
    ax8.get_xaxis().set_major_formatter(matplotlib.dates.DateFormatter('%H:%M'))
    pp.xticks(rotation=70)

    completedBookings = journeys.shape[0] / cfg.nBookings

    pp.legend(bbox_to_anchor=(1.05, 1), loc=2, borderaxespad=0.)

    objectiveValues = pd.DataFrame(columns=['Objective value'])
    objectiveValues.loc["Total battery cost"] = totalBattery
    objectiveValues.loc["Total waiting time [min]"] = waitingTime
    objectiveValues.loc["Total journey time [min]"] = timeInVehicle
    objectiveValues.loc["Average waiting time [min]"] = averageWaitingTime
    objectiveValues.loc["Max waiting time [min]"] = waitingTimeMax
    objectiveValues.loc["Min waiting time [min]"] = waitingTimeMin
    objectiveValues.loc["Stability waiting time (max-min) [min]"] = waitingStability
    objectiveValues.loc["Average journey time [min]"] = averageTimeInVehicle
    objectiveValues.loc["Average occupancy"] = occupancyAvg
    objectiveValues.loc["Average load factor"] = averageLoadFactor
    objectiveValues.loc["Max load factor"] = maxLoadFactor
    objectiveValues.loc["Min load factor"] = minLoadFactor
    objectiveValues.loc["Stability load factor"] = stabilityLoadFactor
    objectiveValues.loc["Percentage of completed bookings"] = completedBookings
    endBatteryLevels = vehicles.groupby('Vehicle ID').apply(lambda t: t[t.TimeStamp == t.TimeStamp.max()])['Battery Level']
    objectiveValues.loc["Average battery level end of simulation"] = endBatteryLevels.mean()
    objectiveValues.loc["Minimum battery level end of simulation"] = endBatteryLevels.min()
    objectiveValues.loc["Maximum battery level end of simulation"] = endBatteryLevels.max()

    objectiveValues.index.names = ['']
    fig9 = h.render_mpl_table(objectiveValues, header_columns=0, col_width=7.0, title = "")

    pdf.savefig(fig9, bbox_inches='tight')
    pdf.savefig(fig10, bbox_inches='tight')
    pdf.savefig(fig1, bbox_inches='tight')
    pdf.savefig(fig2, bbox_inches='tight')
    pdf.savefig(fig3, bbox_inches='tight')
    pdf.savefig(fig4, bbox_inches='tight')
    pdf.savefig(fig5, bbox_inches='tight')
    pdf.savefig(fig8, bbox_inches='tight')

    # If you want you can save some values to a CSV file 
    # Path to file
    # fd = open('optimalValues.csv','a')
    # csvRow = "nameOfSimu"+","+ str(totalBattery) + "," + str(averageWaitingTime) + "," + str(waitingTimeMax) + "," + str(waitingTimeMin) + "," + str(waitingStability) + "," + str(averageTimeInVehicle) + "," + str(occupancyAvg) + "," + str(averageLoadFactor) + "," + str(maxLoadFactor) + "," + str(minLoadFactor) + "," + str(stabilityLoadFactor) + "," + str(completedBookings) + "\n"
    # +str(cfg.nBookings)+","+str(nVehicles)+"," +
    # fd.write(csvRow)
    # fd.close()

pdf.close()
