#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>

#include <cutils/properties.h>

#define MTOP_VERSION "0.6"

typedef struct NodeInfo {
	int	 id;
	char *name;
	FILE *fp;
	unsigned int curcnt;
	unsigned int precnt;
	unsigned long long delta;
} NodeInfo;

typedef struct NodeUnit {
	int	 id;
	char *name;
	unsigned int div;
} NodeUnit;

/**
 * NOTE: we allway put totddr at first array whether this node exit or not,
 * fot the totddr is caculated every time.
 */
NodeInfo nodeInfo_sun9i[] = {
	{ 0, "totddr",  NULL, 0, 0, 0},
	{ 1, "cpuddr0",  NULL, 0, 0, 0},
	{ 2, "gpuddr0",  NULL, 0, 0, 0},
	{ 3, "de_ddr0",  NULL, 0, 0, 0},
	{ 4, "vcfddr0",  NULL, 0, 0, 0},
	{ 5, "othddr0",  NULL, 0, 0, 0},
	{ 6, "cpuddr1",  NULL, 0, 0, 0},
	{ 7, "gpuddr1",  NULL, 0, 0, 0},
	{ 8, "de_ddr1",  NULL, 0, 0, 0},
	{ 9, "vcfddr1",  NULL, 0, 0, 0},
	{ 10, "othddr1",  NULL, 0, 0, 0},
};
NodeUnit nodeUnit_sun9i[] = {
	{ 0, "KB",  0},
	{ 1, "MB",  10},
};

NodeInfo nodeInfo_sun8i[] = {
	{ 0, "totddr", NULL, 0, 0, 0},
	{ 1, "cpuddr", NULL, 0, 0, 0},
	{ 2, "gpuddr", NULL, 0, 0, 0},
	{ 3, "de_ddr", NULL, 0, 0, 0},
	{ 4, "ve_ddr", NULL, 0, 0, 0},
	{ 6, "othddr", NULL, 0, 0, 0},
};
NodeUnit nodeUnit_sun8i[] = {
	{ 0, "KB",  0},
	{ 1, "MB",  1024},
};

NodeInfo nodeInfo_sun8ix[] = {
	{ 0, "totddr", NULL, 0, 0, 0},
	{ 1, "cpuddr", NULL, 0, 0, 0},
	{ 2, "gpuddr", NULL, 0, 0, 0},
	{ 3, "de_ddr", NULL, 0, 0, 0},
	{ 4, "ve_ddr", NULL, 0, 0, 0},
	{ 5, "csiddr", NULL, 0, 0, 0},
	{ 6, "othddr", NULL, 0, 0, 0},
};
NodeUnit nodeUnit_sun8ix[] = {
	{ 0, "KB",  0},
	{ 1, "MB",  1024},
};

NodeInfo nodeInfo_sun50i[] = {
	{ 0, "totddr", NULL, 0, 0, 0},
	{ 1, "cpuddr", NULL, 0, 0, 0},
	{ 2, "gpuddr", NULL, 0, 0, 0},
	{ 3, "de_ddr", NULL, 0, 0, 0},
	{ 4, "ve_ddr", NULL, 0, 0, 0},
	{ 5, "csiddr", NULL, 0, 0, 0},
	{ 6, "othddr", NULL, 0, 0, 0},
};
NodeUnit nodeUnit_sun50i[] = {
	{ 0, "KB",   0},
	{ 1, "MB",  1024},
};

NodeInfo *nodeInfo;
NodeUnit *nodeUnit;

unsigned int max;
unsigned long long total;
unsigned long long idx;

int nodeCnt;

int delay;
int iter;
int mUnit = 0;
int latency;
unsigned int per;
char output_fn[256];
FILE *output_fp;

int nhardware;
char path_prefix[256];

#define GTBUS_PMU_DIR "/sys/devices/platform/GTBUS_PMU"
#define MBUS_PMU_DIR "/sys/class/hwmon/mbus_pmu/device"
#define HWMON0_DIR "/sys/class/hwmon/hwmon1"

static int mtop_baner();
static int mtop_read();
static void mtop_post();
static void mtop_update();

static void usage(char *program)
{
	fprintf(stdout, "\n");
	fprintf(stdout, "Usage: %s [-n iter] [-d delay] [-m] [-o FILE] [-h]\n", program);
	fprintf(stdout, "    -n NUM   Updates to show before exiting.\n");
	fprintf(stdout, "    -d NUM   Seconds to wait between update.\n");
	fprintf(stdout, "    -m unit: %s\n", mUnit ? nodeUnit[1].name : nodeUnit[0].name);
	fprintf(stdout, "    -o FILE  Output to a file.\n");
	fprintf(stdout, "    -v Display mtop version.\n");
	fprintf(stdout, "    -h Display this help screen.\n");
	fprintf(stdout, "\n");
}

static void version(void)
{
	fprintf(stdout, "\n");
	fprintf(stdout, "mtop version: %s\n", MTOP_VERSION);
	fprintf(stdout, "hardware support: V40/V66 \n");
	fprintf(stdout, "last update time : 2016-01-18 \n");
	fprintf(stdout, "\n");
}

int main(int argc, char *argv[])
{
	int i;
	unsigned long value, bandwidth;
	char hardware[PROPERTY_VALUE_MAX];

	property_get("ro.hardware", hardware, "sun9i");
	if (!strncmp(hardware, "sun9i", strlen("sun9i"))) {
		nhardware = 0;
		nodeCnt = sizeof(nodeInfo_sun9i)/sizeof(nodeInfo_sun9i[0]);
		strncpy(path_prefix, GTBUS_PMU_DIR, sizeof(path_prefix));
		nodeInfo = nodeInfo_sun9i;
		nodeUnit = nodeUnit_sun9i;
	} else if (!strncmp(hardware, "sun8i", strlen("sun8i"))) {
		nhardware = 1;
		nodeCnt = sizeof(nodeInfo_sun8i)/sizeof(nodeInfo_sun8i[0]);
		strncpy(path_prefix, MBUS_PMU_DIR, sizeof(path_prefix));
		nodeInfo = nodeInfo_sun8i;
		nodeUnit = nodeUnit_sun8i;
	} else if (!strncmp(hardware, "sun8iw11p1", strlen("sun8iw11p1"))) {
		nhardware = 1;
		nodeCnt = sizeof(nodeInfo_sun8ix)/sizeof(nodeInfo_sun8ix[0]);
		strncpy(path_prefix, MBUS_PMU_DIR, sizeof(path_prefix));
		nodeInfo = nodeInfo_sun8ix;
		nodeUnit = nodeUnit_sun8ix;
        } else if (!strncmp(hardware, "sun8iw15p1", strlen("sun8iw15p1"))) {
                nhardware = 1;
                nodeCnt = sizeof(nodeInfo_sun8ix)/sizeof(nodeInfo_sun8ix[0]);
                strncpy(path_prefix, HWMON0_DIR, sizeof(path_prefix));
                nodeInfo = nodeInfo_sun8ix;
                nodeUnit = nodeUnit_sun8ix;
	} else if (!strncmp(hardware, "sun50i", strlen("sun50i"))) {
		nhardware = 2;
		nodeCnt = sizeof(nodeInfo_sun50i)/sizeof(nodeInfo_sun50i[0]);
		strncpy(path_prefix, HWMON0_DIR, sizeof(path_prefix));
		nodeInfo = nodeInfo_sun50i;
		nodeUnit = nodeUnit_sun50i;
	} else {
		fprintf(stdout, "not supported platform, deal as sun8i platform!\n");
	}
	/*
	 * linux4.4 use /sys/class/hwmon/hwmon0
	 */
	{
		FILE *check_file;
		check_file = fopen(path_prefix, "r");
		if (check_file == NULL) {
			memset(path_prefix, 0, sizeof(path_prefix));
			strncpy(path_prefix, HWMON0_DIR, sizeof(path_prefix));
		}
		else {
			fclose(check_file);
		}
	}

	max = 0;
	total = 0;
	idx = 0;

	delay = 1;
	iter = -1;
	mUnit = 0;
	latency = 0;
	per = 0xffffffff;
	memset(output_fn, 0, sizeof(output_fn));
	output_fp = NULL;

	for (i = 1; i < argc; i++) {
		if (!strcmp(argv[i], "-n")) {
			if (i + 1 >= argc) {
				fprintf(stderr, "Option -n expects an argument.\n");
				usage(argv[0]);
				exit(-1);
			}
			iter = atoi(argv[++i]);
			// FIXME
			continue;
		}

		if (!strcmp(argv[i], "-d")) {
			if (i + 1 >= argc) {
				fprintf(stderr, "Option -d expects an argument.\n");
				usage(argv[0]);
				exit(-1);
			}
			delay = atoi(argv[++i]);
			// FIXME
			continue;
		}

		if (!strcmp(argv[i], "-m")) {
			mUnit = 1;
			continue;
		}

		if (!strcmp(argv[i], "-l")) {
			latency = 1;
			continue;
		}
/*
		if (!strcmp(argv[i], "-p")) {
			per = 1;
			continue;
		}
*/
		if (!strcmp(argv[i], "-o")) {
			if (i + 1 >= argc) {
				fprintf(stderr, "Option -o expects an argument.\n");
				usage(argv[0]);
				exit(-1);
			}
			strncpy(output_fn, argv[++i], 256);
			continue;
		}

		if (!strcmp(argv[i], "-v")) {
			version();
			exit(0);
		}

		if (!strcmp(argv[i], "-h")) {
			usage(argv[0]);
			exit(0);
		}

		fprintf(stderr, "Invalid argument \"%s\".\n", argv[i]);
		usage(argv[0]);
		exit(-1);
	}

	fprintf(stdout, "\n");
	fprintf(stdout, "iter: %d\n", iter);
	fprintf(stdout, "dealy: %d\n", delay);
	fprintf(stdout, "unit: %s\n", mUnit ? nodeUnit[1].name : nodeUnit[0].name);
	fprintf(stdout, "output: %s\n", output_fn);
	fprintf(stdout, "\n");

	if (output_fn[0]) {
		output_fp = fopen(output_fn, "w");
		if (NULL == output_fp) {
			fprintf(stdout, "Could not open file %s: %s\n", output_fn, strerror(errno));
			exit(-1);
		}

		mtop_baner();
	}

	mtop_read();
	mtop_post();

	while (iter == -1 || iter-- > 0) {
		sleep(delay);
		mtop_read();
		mtop_update();
		mtop_post();
	}

	if (output_fp) {
		fclose(output_fp);
	}

	return 0;
}

static int mtop_baner(void)
{
	int i;

	for (i = 1; i < nodeCnt; i++) {
		if (per & (1UL << i)) {
			fwrite(nodeInfo[i].name, strlen(nodeInfo[i].name), 1, output_fp);
			if (i+1 < nodeCnt && per & (1UL << (i+1))) {
				fwrite(" ", 1, 1, output_fp);
			}
		}
	}

	fwrite("\n", 1, 1, output_fp);

	return 0;
}

static int mtop_read(void)
{
	int i;
	char path[256];

	for (i = 1; i < nodeCnt; i++) {
		if (per & (1UL << i)) {
			snprintf(path, sizeof(path), "%s/pmu_%s", path_prefix, nodeInfo[i].name);
			nodeInfo[i].fp = fopen(path, "r");
			if (NULL == nodeInfo[i].fp) {
				fprintf(stderr, "Could not open file %s: %s\n", path, strerror(errno));
				goto open_error;
			}

			fscanf(nodeInfo[i].fp, "%u", &nodeInfo[i].curcnt);
			fclose(nodeInfo[i].fp);
			nodeInfo[i].fp = NULL;
		}
	}

	return 0;

open_error:
	for (i = 1; i < nodeCnt; i++) {
		if (nodeInfo[i].fp) {
			fclose(nodeInfo[i].fp);
			nodeInfo[i].fp = NULL;
		}
	}
	return -1;
}

static void mtop_post(void)
{
	int i;

	for (i = 1; i < nodeCnt; i++) {
		if (per & (1UL << i)) {
			nodeInfo[i].precnt = nodeInfo[i].curcnt;
		}
	}
}

static void mtop_update(void)
{
	int i;
	unsigned int cur_total;
	unsigned int average;
	char buf[1024];

	cur_total = 0;

	nodeInfo[0].delta = 0;
	for (i = 1; i < nodeCnt; i++) {
		if (per & (1UL << i)) {
			if (nodeInfo[i].precnt <= nodeInfo[i].curcnt)
				nodeInfo[i].delta = nodeInfo[i].curcnt - nodeInfo[i].precnt;
			else
				nodeInfo[i].delta = (unsigned int)((nodeInfo[i].curcnt + (unsigned long long)(2^32)) - nodeInfo[i].precnt);

			if (mUnit)
				nodeInfo[i].delta /= nodeUnit[1].div;

			cur_total += nodeInfo[i].delta;
		}
	}
	nodeInfo[0].delta = cur_total;

	if (cur_total > max)
		max = cur_total;
	total += cur_total;
	idx++;
	average = total / idx;
		
	fprintf(stdout, "total: %llu, ", total);
	fprintf(stdout, "num: %llu, ", idx);
	fprintf(stdout, "Max: %u, ", max);
	fprintf(stdout, "Average: %u\n", average);

	for (i = 0; i < nodeCnt; i++)
		fprintf(stdout, " %7s", nodeInfo[i].name);
	fprintf(stdout, "\n");
	for (i = 0; i < nodeCnt; i++)
		fprintf(stdout, " %7llu", nodeInfo[i].delta);
	fprintf(stdout, "\n");

	if (cur_total == 0)
		cur_total++;
	for (i = 0; i < nodeCnt; i++)
		fprintf(stdout, " %7.2f", (float)nodeInfo[i].delta*100/cur_total);
	fprintf(stdout, "\n\n");

	if (output_fp) {
		fwrite("\n", 1, 1, output_fp);
		fflush(output_fp);
	}
}
