import numpy as np
import pandas as pd
import matplotlib.pyplot as pp
import seaborn
import sys
import helpers as h
import matplotlib.backends.backend_pdf
import seaborn
import sys
import six
import datetime

data = pd.read_csv('plots/journey_logs.csv')

pdf = matplotlib.backends.backend_pdf.PdfPages("plots/journeys.pdf")

if data.size != 0:
	fig1, ax1 = pp.subplots()
	df = h.convertColumns(data, ['Booking Time','Pick-up Time','Drop-off Time'], h.todatetime)
	df['Waiting Time'] = (df['Pick-up Time'] - df['Booking Time']).apply(lambda x: x.seconds/60)
	df_sorted = df.sort_values(by=['Booking Time'])
	df_hr = h.convertColumns(df_sorted, ['Booking Time','Pick-up Time','Drop-off Time'], h.tohr)
	wait_hr_avg = df_hr.groupby('Booking Time').mean()

	ax1.set_title('Avg. hourly waiting time')
	ax1.plot(wait_hr_avg.index.hour, wait_hr_avg['Waiting Time'])
	ax1.set_xlabel('Hour')
	ax1.set_ylabel('Avg. Waiting Time (minutes)')

	seats_hr = df_hr.groupby('Booking Time').sum()
	fig2, ax2 = pp.subplots()
	ax2.set_title('Hourly seats booked')
	ax2.plot(wait_hr_avg.index.hour, seats_hr['Seat Count'])
	ax2.set_xlabel('Hour')
	ax2.set_ylabel('Seats booked')

	pdf.savefig(fig1, bbox_inches='tight')
	pdf.savefig(fig2, bbox_inches='tight')
pdf.close()



